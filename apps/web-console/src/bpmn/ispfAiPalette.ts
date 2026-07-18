/**
 * ADR-0049: palette entries for AI service tasks (llm_complete / invoke_agent).
 */
import type { PaletteEntries } from "./ispfPaletteFilter";

type BpmnFactory = {
  create: (type: string, attrs?: Record<string, unknown>) => {
    name?: string;
    set: (key: string, value: string) => void;
  };
};

type ElementFactory = {
  createShape: (attrs: Record<string, unknown>) => unknown;
};

type Create = {
  start: (event: Event, shape: unknown) => void;
};

type Palette = {
  registerProvider: (
    priority: number,
    provider: { getPaletteEntries: () => PaletteEntries }
  ) => void;
};

function applyAiDefaults(
  businessObject: { set: (key: string, value: string) => void },
  action: "llm_complete" | "invoke_agent"
) {
  businessObject.set("action", action);
  if (action === "llm_complete") {
    businessObject.set("promptTemplate", "Classify: ${alarmMessage}. Reply JSON {severity,reason}");
    businessObject.set("outputVariable", "llmClassification");
    businessObject.set("outputFormat", "json");
    businessObject.set("modelRef", "platform-default");
    return;
  }
  businessObject.set("goalTemplate", "Explain trend for ${tagPath} last 4h");
  businessObject.set("agentMode", "ask");
  businessObject.set("toolAllowlist", "get_variable_history,summarize_trend,detect_anomalies");
  businessObject.set("maxSteps", "8");
  businessObject.set("outputVariable", "agentBrief");
}

export function createIspfAiPaletteProvider() {
  class IspfAiPalette {
    static $inject = ["palette", "create", "elementFactory", "bpmnFactory"];

    private readonly _create: Create;
    private readonly _elementFactory: ElementFactory;
    private readonly _bpmnFactory: BpmnFactory;

    constructor(palette: Palette, create: Create, elementFactory: ElementFactory, bpmnFactory: BpmnFactory) {
      this._create = create;
      this._elementFactory = elementFactory;
      this._bpmnFactory = bpmnFactory;
      palette.registerProvider(600, this);
    }

    getPaletteEntries(): PaletteEntries {
      return {
        "create.ispf-llm-complete": {
          group: "activity",
          className: "bpmn-icon-service-task",
          title: "Create AI: LLM Complete",
          action: {
            dragstart: (event: Event) => this.createAiTask(event, "llm_complete", "LLM Complete"),
            click: (event: Event) => this.createAiTask(event, "llm_complete", "LLM Complete"),
          },
        },
        "create.ispf-invoke-agent": {
          group: "activity",
          className: "bpmn-icon-service-task",
          title: "Create AI: Invoke Agent",
          action: {
            dragstart: (event: Event) => this.createAiTask(event, "invoke_agent", "Invoke Agent"),
            click: (event: Event) => this.createAiTask(event, "invoke_agent", "Invoke Agent"),
          },
        },
      };
    }

    private createAiTask(
      event: Event,
      action: "llm_complete" | "invoke_agent",
      name: string
    ) {
      const businessObject = this._bpmnFactory.create("bpmn:ServiceTask", { name });
      applyAiDefaults(businessObject, action);
      const shape = this._elementFactory.createShape({
        type: "bpmn:ServiceTask",
        businessObject,
      });
      this._create.start(event, shape);
    }
  }

  return {
    __init__: ["ispfAiPalette"],
    ispfAiPalette: ["type", IspfAiPalette],
  };
}
