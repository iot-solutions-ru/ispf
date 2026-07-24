import { deserializeWorkflowDiagram } from "./model/deserialize";
import { serializeWorkflowDiagram } from "./model/serialize";
import type { FlowNode, WorkflowDiagram } from "./model/types";

export interface AdaptWarning {
  code: string;
  message: string;
  elementId?: string;
}

export interface AdaptForeignBpmnResult {
  xml: string;
  diagram: WorkflowDiagram;
  warnings: AdaptWarning[];
}

const CAMUNDA_NS = "http://camunda.org/schema/1.0/bpmn";
const FLOWABLE_NS = "http://flowable.org/bpmn";

/** Strip vendor namespaces / attrs and map known patterns onto ISPF model. */
export function adaptForeignBpmn(rawXml: string): AdaptForeignBpmnResult {
  const warnings: AdaptWarning[] = [];
  let xml = rawXml?.trim() || "";
  if (!xml) {
    const empty = deserializeWorkflowDiagram("");
    return { xml: serializeWorkflowDiagram(empty), diagram: empty, warnings };
  }

  // Detect foreign namespaces before strip
  if (xml.includes("camunda.org") || xml.includes("xmlns:camunda")) {
    warnings.push({
      code: "foreign.camunda",
      message: "Camunda extensions detected; mapped or dropped where possible.",
    });
  }
  if (xml.includes("flowable.org") || xml.includes("xmlns:flowable")) {
    warnings.push({
      code: "foreign.flowable",
      message: "Flowable extensions detected; mapped or dropped where possible.",
    });
  }

  xml = stripVendorAttributes(xml);
  xml = ensureIspfNamespace(xml);
  xml = rewriteCallActivities(xml, warnings);
  xml = rewriteGenericTasks(xml, warnings);
  xml = dropUnsupported(xml, warnings);

  let diagram: WorkflowDiagram;
  try {
    diagram = deserializeWorkflowDiagram(xml);
  } catch (error) {
    warnings.push({
      code: "parse.failed",
      message: (error as Error).message,
    });
    diagram = deserializeWorkflowDiagram("");
    return { xml: serializeWorkflowDiagram(diagram), diagram, warnings };
  }

  for (const node of diagram.nodes) {
    mapNodeIspf(node, warnings);
  }

  // Drop multi-instance leftovers if still present as unknown — already dropped in XML
  const outXml = serializeWorkflowDiagram(diagram);
  return { xml: outXml, diagram, warnings };
}

function ensureIspfNamespace(xml: string): string {
  if (xml.includes("xmlns:ispf=") || xml.includes('xmlns:ispf="')) return xml;
  return xml.replace(
    /<definitions\b([^>]*)>/i,
    `<definitions$1 xmlns:ispf="http://ispf.io/bpmn">`
  );
}

function stripVendorAttributes(xml: string): string {
  let next = xml;
  // Remove xmlns:camunda / flowable declarations
  next = next.replace(/\sxmlns:camunda="[^"]*"/g, "");
  next = next.replace(/\sxmlns:flowable="[^"]*"/g, "");
  // Remove camunda:* and flowable:* attributes
  next = next.replace(/\s(?:camunda|flowable):[a-zA-Z0-9_.:-]+="[^"]*"/g, "");
  // Remove modeler namespaces that confuse our serializer roundtrip
  next = next.replace(/\sxmlns:modeler="[^"]*"/g, "");
  next = next.replace(/\smodeler:[a-zA-Z0-9_.:-]+="[^"]*"/g, "");
  void CAMUNDA_NS;
  void FLOWABLE_NS;
  return next;
}

function rewriteCallActivities(xml: string, warnings: AdaptWarning[]): string {
  return xml.replace(
    /<callActivity\b([^>]*)(?:\/>|>([\s\S]*?)<\/callActivity>)/gi,
    (full, attrs: string) => {
      const idMatch = /\bid="([^"]+)"/.exec(attrs);
      const called =
        /\bcalledElement="([^"]+)"/.exec(attrs)?.[1] ||
        /\bcamunda:calledElement="([^"]+)"/.exec(attrs)?.[1] ||
        /\bispf:workflowPath="([^"]+)"/.exec(attrs)?.[1] ||
        "";
      const id = idMatch?.[1] || "callActivity_adapted";
      if (!called) {
        warnings.push({
          code: "map.callActivity",
          message: `callActivity «${id}» has no calledElement / workflowPath — set ispf:workflowPath before run.`,
          elementId: id,
        });
        return full;
      }
      const path = called.includes(".") ? called : `root.platform.workflows.${called}`;
      if (/\bispf:workflowPath=/.test(attrs)) {
        return full;
      }
      warnings.push({
        code: "map.callActivity",
        message: `callActivity «${id}» kept (parent waits); set ispf:workflowPath from calledElement.`,
        elementId: id,
      });
      if (full.endsWith("/>")) {
        return full.replace(/\/>$/, ` ispf:workflowPath="${path}"/>`);
      }
      return full.replace(/>/, ` ispf:workflowPath="${path}">`);
    }
  );
}

function rewriteGenericTasks(xml: string, warnings: AdaptWarning[]): string {
  return xml.replace(/<task\b([^>]*)(?:\/>|>([\s\S]*?)<\/task>)/gi, (_full, attrs: string) => {
    const idMatch = /\bid="([^"]+)"/.exec(attrs);
    const nameMatch = /\bname="([^"]+)"/.exec(attrs);
    const id = idMatch?.[1] || "Task_adapted";
    const name = nameMatch?.[1] || "Task";
    warnings.push({
      code: "map.task",
      message: `Generic task «${id}» rewritten to serviceTask log.`,
      elementId: id,
    });
    return `<serviceTask id="${id}" name="${name}" ispf:action="log" ispf:message="${name}"/>`;
  });
}

function dropUnsupported(xml: string, warnings: AdaptWarning[]): string {
  const patterns: Array<{ re: RegExp; code: string; label: string }> = [
    {
      re: /<multiInstanceLoopCharacteristics\b[\s\S]*?<\/multiInstanceLoopCharacteristics>/gi,
      code: "drop.multiInstance",
      label: "multiInstanceLoopCharacteristics",
    },
    {
      re: /<businessRuleTask\b[^>]*\/>|<businessRuleTask\b[\s\S]*?<\/businessRuleTask>/gi,
      code: "drop.businessRuleTask",
      label: "businessRuleTask",
    },
    {
      re: /<inclusiveGateway\b[^>]*\/>|<inclusiveGateway\b[\s\S]*?<\/inclusiveGateway>/gi,
      code: "drop.inclusiveGateway",
      label: "inclusiveGateway",
    },
    {
      re: /<eventBasedGateway\b[^>]*\/>|<eventBasedGateway\b[\s\S]*?<\/eventBasedGateway>/gi,
      code: "drop.eventBasedGateway",
      label: "eventBasedGateway",
    },
  ];
  let next = xml;
  for (const { re, code, label } of patterns) {
    if (re.test(next)) {
      warnings.push({
        code,
        message: `Removed unsupported ${label} (not in ISPF subset). Re-model if needed.`,
      });
      next = next.replace(re, "");
    }
  }
  // data objects / stores — remove elements
  next = next.replace(
    /<(dataObject|dataObjectReference|dataStore|dataStoreReference)\b[^>]*\/>|<(dataObject|dataObjectReference|dataStore|dataStoreReference)\b[\s\S]*?<\/\2>/gi,
    () => {
      warnings.push({
        code: "drop.data",
        message: "Removed data object/store (not executed by ISPF).",
      });
      return "";
    }
  );
  return next;
}

function mapNodeIspf(node: FlowNode, warnings: AdaptWarning[]): void {
  if (node.type === "serviceTask" && !node.ispf.action) {
    node.ispf.action = "log";
    if (!node.ispf.message) node.ispf.message = node.name || node.id;
    warnings.push({
      code: "default.serviceTask",
      message: `serviceTask «${node.id}» had no ispf:action; defaulted to log.`,
      elementId: node.id,
    });
  }
  if (node.type === "intermediateThrowEvent" && !node.ispf.message) {
    node.ispf.message = node.name || "message";
    warnings.push({
      code: "default.messageThrow",
      message: `Throw event «${node.id}» missing message name; set ispf:message.`,
      elementId: node.id,
    });
  }
}
