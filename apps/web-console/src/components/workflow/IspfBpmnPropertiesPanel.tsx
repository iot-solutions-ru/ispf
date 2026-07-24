import { useTranslation } from "react-i18next";
import { WORKFLOW_ISPF_ACTIONS, type WorkflowIspfAction } from "../../types/automation";
import type { CatchKind, FlowEdge, FlowNode } from "../../bpmn/model/types";

const ACTION_ATTRS: Record<WorkflowIspfAction, string[]> = {
  fire_event: ["objectPath", "eventName", "payloadJson"],
  read_variable: ["objectPath", "variable", "contextKey"],
  start_workflow: ["workflowPath", "targetObject"],
  invoke_function: ["objectPath", "functionName", "inputMap"],
  setVariable: ["targetObject", "variable", "value"],
  log: ["message"],
  publishNats: ["subject", "message", "channel"],
  llm_complete: ["promptTemplate", "outputVariable", "outputFormat", "modelRef", "timeoutMs"],
  invoke_agent: ["goalTemplate", "agentMode", "toolAllowlist", "maxSteps", "outputVariable"],
};

export type Selection =
  | { kind: "node"; node: FlowNode }
  | { kind: "edge"; edge: FlowEdge }
  | null;

interface Props {
  selection: Selection;
  onChangeNode: (id: string, patch: Partial<FlowNode>) => void;
  onChangeEdge: (id: string, patch: Partial<FlowEdge>) => void;
  onDelete: () => void;
}

export default function IspfBpmnPropertiesPanel({
  selection,
  onChangeNode,
  onChangeEdge,
  onDelete,
}: Props) {
  const { t } = useTranslation("workflow");

  if (!selection) {
    return (
      <aside className="bpmn-ispf-panel">
        <h4>{t("bpmn.props.title")}</h4>
        <p className="hint">{t("bpmn.props.empty")}</p>
        <p className="hint">{t("bpmn.notationSubset")}</p>
      </aside>
    );
  }

  if (selection.kind === "edge") {
    const edge = selection.edge;
    return (
      <aside className="bpmn-ispf-panel">
        <h4>{t("bpmn.props.sequenceFlow")}</h4>
        <p className="hint">{edge.id}</p>
        <label>
          {t("bpmn.props.name")}
          <input
            value={edge.name ?? ""}
            onChange={(e) => onChangeEdge(edge.id, { name: e.target.value })}
          />
        </label>
        <label>
          ispf:condition
          <input
            value={edge.condition ?? ""}
            onChange={(e) => onChangeEdge(edge.id, { condition: e.target.value })}
            placeholder="CEL expression"
          />
        </label>
        <label className="bpmn-prop-checkbox">
          <input
            type="checkbox"
            checked={Boolean(edge.isDefault)}
            onChange={(e) => onChangeEdge(edge.id, { isDefault: e.target.checked })}
          />
          ispf:default
        </label>
        <button type="button" className="btn danger" onClick={onDelete}>
          {t("bpmn.props.delete")}
        </button>
      </aside>
    );
  }

  const node = selection.node;
  const action = (node.ispf.action || "") as WorkflowIspfAction | "";
  const hintAttrs = action && ACTION_ATTRS[action] ? ACTION_ATTRS[action] : [];

  function setIspf(key: string, value: string) {
    onChangeNode(node.id, { ispf: { ...node.ispf, [key]: value } });
  }

  return (
    <aside className="bpmn-ispf-panel">
      <h4>{t("bpmn.props.title")}</h4>
      <p className="hint">
        {t("bpmn.element", { type: node.type, id: node.id })}
      </p>

      <section className="bpmn-prop-section">
        <h5>{t("bpmn.props.tabGeneral")}</h5>
        <label>
          {t("bpmn.props.name")}
          <input
            value={node.name}
            onChange={(e) => onChangeNode(node.id, { name: e.target.value })}
          />
        </label>
      </section>

      {(node.type === "serviceTask" || node.type === "messageTask") && (
        <section className="bpmn-prop-section">
          <h5>{t("bpmn.props.tabImplementation")}</h5>
          {node.type === "serviceTask" && (
            <label>
              ispf:action
              <select value={action} onChange={(e) => setIspf("action", e.target.value)}>
                <option value="">—</option>
                {WORKFLOW_ISPF_ACTIONS.map((entry) => (
                  <option key={entry.action} value={entry.action}>
                    {entry.action} — {entry.label}
                  </option>
                ))}
              </select>
            </label>
          )}
          {node.type === "messageTask" &&
            ["subject", "message", "channel"].map((attr) => (
              <label key={attr}>
                ispf:{attr}
                <input value={node.ispf[attr] ?? ""} onChange={(e) => setIspf(attr, e.target.value)} />
              </label>
            ))}
          {hintAttrs.map((attr) => (
            <label key={attr}>
              ispf:{attr}
              <input
                value={node.ispf[attr] ?? ""}
                onChange={(e) => setIspf(attr, e.target.value)}
              />
            </label>
          ))}
        </section>
      )}

      {node.type === "userTask" && (
        <section className="bpmn-prop-section">
          <h5>{t("bpmn.props.tabImplementation")}</h5>
          {["title", "instructions", "assigneeRole", "targetObject", "function"].map((attr) => (
            <label key={attr}>
              ispf:{attr}
              <input value={node.ispf[attr] ?? ""} onChange={(e) => setIspf(attr, e.target.value)} />
            </label>
          ))}
        </section>
      )}

      {node.type === "callActivity" && (
        <section className="bpmn-prop-section">
          <h5>{t("bpmn.props.tabImplementation")}</h5>
          <p className="hint">{t("bpmn.props.callActivityHint")}</p>
          {["workflowPath", "objectPath", "inputMap"].map((attr) => (
            <label key={attr}>
              ispf:{attr}
              <input value={node.ispf[attr] ?? ""} onChange={(e) => setIspf(attr, e.target.value)} />
            </label>
          ))}
        </section>
      )}

      {(node.type === "intermediateCatchEvent" || node.type === "boundaryEvent") && (
        <section className="bpmn-prop-section">
          <h5>{t("bpmn.props.tabEvents")}</h5>
          {node.type === "intermediateCatchEvent" && (
            <label>
              {t("bpmn.props.catchKind")}
              <select
                value={node.catchKind || "signal"}
                onChange={(e) => {
                  const catchKind = e.target.value as CatchKind;
                  const ispf = { ...node.ispf };
                  if (catchKind === "timer") {
                    delete ispf.signal;
                    delete ispf.message;
                    if (!ispf.durationSeconds) ispf.durationSeconds = "60";
                  } else if (catchKind === "signal") {
                    delete ispf.durationSeconds;
                    delete ispf.message;
                    if (!ispf.signal) ispf.signal = "signalName";
                  } else {
                    delete ispf.durationSeconds;
                    delete ispf.signal;
                    if (!ispf.message) ispf.message = "messageName";
                  }
                  onChangeNode(node.id, { catchKind, ispf });
                }}
              >
                <option value="timer">timer</option>
                <option value="signal">signal</option>
                <option value="message">message</option>
              </select>
            </label>
          )}
          {(node.catchKind === "timer" || node.type === "boundaryEvent") && (
            <label>
              ispf:durationSeconds
              <input
                value={node.ispf.durationSeconds ?? ""}
                onChange={(e) => setIspf("durationSeconds", e.target.value)}
              />
            </label>
          )}
          {node.catchKind === "signal" && (
            <label>
              ispf:signal
              <input value={node.ispf.signal ?? ""} onChange={(e) => setIspf("signal", e.target.value)} />
            </label>
          )}
          {node.catchKind === "message" && (
            <label>
              ispf:message
              <input
                value={node.ispf.message ?? ""}
                onChange={(e) => setIspf("message", e.target.value)}
              />
            </label>
          )}
          {node.type === "boundaryEvent" && (
            <>
              <label>
                attachedToRef
                <input
                  value={node.attachedToRef ?? ""}
                  onChange={(e) => onChangeNode(node.id, { attachedToRef: e.target.value })}
                />
              </label>
              <label className="bpmn-prop-checkbox">
                <input
                  type="checkbox"
                  checked={node.cancelActivity !== false}
                  onChange={(e) => onChangeNode(node.id, { cancelActivity: e.target.checked })}
                />
                cancelActivity
              </label>
            </>
          )}
        </section>
      )}

      {node.type === "intermediateThrowEvent" && (
        <section className="bpmn-prop-section">
          <h5>{t("bpmn.props.tabEvents")}</h5>
          <p className="hint">{t("bpmn.props.messageThrowOnly")}</p>
          <label>
            ispf:message
            <input
              value={node.ispf.message ?? ""}
              onChange={(e) => setIspf("message", e.target.value)}
            />
          </label>
        </section>
      )}

      {(node.type === "startEvent" || node.type === "endEvent") && (
        <p className="hint">{t("bpmn.props.startEndHint")}</p>
      )}

      <button type="button" className="btn danger" onClick={onDelete}>
        {t("bpmn.props.delete")}
      </button>
    </aside>
  );
}
