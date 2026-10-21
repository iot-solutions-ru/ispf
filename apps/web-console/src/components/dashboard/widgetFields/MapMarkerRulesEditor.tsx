import type { TFunction } from "i18next";
import { useEffect, useState, type ReactNode } from "react";
import { type MapMarkerRule, type MapMarkerRuleOp } from "../../../types/dashboard";
import { parseMarkerRules, serializeMarkerRules } from "../mapWidgetUtils";
import WidgetMediaUploadField from "../WidgetMediaUploadField";
import { WidgetEditorCaption } from "./widgetFieldPrimitives";

const MAP_MARKER_OPS: MapMarkerRuleOp[] = ["eq", "ne", "gt", "gte", "lt", "lte", "in"];

function newMarkerRule(): MapMarkerRule {
  return {
    id: `r-${Date.now().toString(36)}`,
    when: { variable: "driverStatus", op: "eq", value: "RUNNING" },
    style: { type: "color", color: "#3fb950" },
  };
}

export default function MapMarkerRulesEditor({
  value,
  onChange,
  t,
}: {
  value: string | undefined;
  onChange: (next: string | undefined) => void;
  t: TFunction;
}): ReactNode {
  // Keep local draft so Color→Picture (empty URL) does not drop the row on re-parse.
  const [rules, setRules] = useState<MapMarkerRule[]>(() => parseMarkerRules(value));

  useEffect(() => {
    const incoming = serializeMarkerRules(parseMarkerRules(value)) ?? "";
    const current = serializeMarkerRules(rules) ?? "";
    if (incoming !== current) {
      setRules(parseMarkerRules(value));
    }
    // Only re-sync when parent value changes externally.
    // eslint-disable-next-line react-hooks/exhaustive-deps -- intentional: compare against value
  }, [value]);

  const commit = (next: MapMarkerRule[]) => {
    setRules(next);
    onChange(serializeMarkerRules(next));
  };

  const patch = (index: number, mut: (rule: MapMarkerRule) => MapMarkerRule) => {
    commit(rules.map((rule, i) => (i === index ? mut(rule) : rule)));
  };

  return (
    <div className="widget-editor-map-marker-rules full">
      <p className="hint">{t("editor.hint.markerRules")}</p>
      {rules.map((rule, index) => {
        const styleType = rule.style.type;
        return (
          <div key={rule.id ?? `rule-${index}`} className="widget-editor-map-marker-rule">
            <div className="widget-editor-map-marker-rule-head">
              <strong>
                {t("editor.markerRule.state")} {index + 1}
              </strong>
              <div className="widget-editor-list-actions">
                <button
                  type="button"
                  disabled={index === 0}
                  onClick={() => {
                    if (index === 0) return;
                    const next = [...rules];
                    [next[index - 1], next[index]] = [next[index], next[index - 1]];
                    commit(next);
                  }}
                >
                  ↑
                </button>
                <button
                  type="button"
                  disabled={index >= rules.length - 1}
                  onClick={() => {
                    if (index >= rules.length - 1) return;
                    const next = [...rules];
                    [next[index], next[index + 1]] = [next[index + 1], next[index]];
                    commit(next);
                  }}
                >
                  ↓
                </button>
                <button type="button" onClick={() => commit(rules.filter((_, i) => i !== index))}>
                  {t("editor.markerRule.remove")}
                </button>
              </div>
            </div>

            <div className="widget-editor-map-marker-rule-block">
              <span className="widget-editor-map-marker-rule-block-title">
                {t("editor.markerRule.condition")}
              </span>
              <div className="widget-editor-map-marker-rule-row">
                <label>
                  <WidgetEditorCaption text={t("editor.markerRule.variable")} />
                  <input
                    value={rule.when.variable}
                    onChange={(e) =>
                      patch(index, (r) => ({
                        ...r,
                        when: { ...r.when, variable: e.target.value },
                      }))
                    }
                    placeholder="driverStatus"
                  />
                </label>
                <label>
                  <WidgetEditorCaption text={t("editor.markerRule.op")} />
                  <select
                    value={rule.when.op}
                    onChange={(e) =>
                      patch(index, (r) => ({
                        ...r,
                        when: { ...r.when, op: e.target.value as MapMarkerRuleOp },
                      }))
                    }
                  >
                    {MAP_MARKER_OPS.map((op) => (
                      <option key={op} value={op}>
                        {t(`editor.markerRule.op.${op}`)}
                      </option>
                    ))}
                  </select>
                </label>
                <label>
                  <WidgetEditorCaption text={t("editor.markerRule.value")} />
                  <input
                    value={String(rule.when.value ?? "")}
                    onChange={(e) => {
                      const raw = e.target.value;
                      const asNum = Number(raw);
                      const nextValue =
                        raw.trim() !== "" &&
                        Number.isFinite(asNum) &&
                        /^-?\d+(\.\d+)?$/.test(raw.trim())
                          ? asNum
                          : raw;
                      patch(index, (r) => ({ ...r, when: { ...r.when, value: nextValue } }));
                    }}
                    placeholder="RUNNING"
                  />
                </label>
              </div>
            </div>

            <div className="widget-editor-map-marker-rule-block">
              <span className="widget-editor-map-marker-rule-block-title">
                {t("editor.markerRule.appearance")}
              </span>
              <label>
                <WidgetEditorCaption text={t("editor.markerRule.styleType")} />
                <select
                  value={styleType}
                  onChange={(e) => {
                    const nextType = e.target.value as "color" | "icon";
                    patch(index, (r) =>
                      nextType === "color"
                        ? {
                            ...r,
                            style: {
                              type: "color",
                              color: r.style.type === "color" ? r.style.color : "#3fb950",
                            },
                          }
                        : {
                            ...r,
                            style: {
                              type: "icon",
                              url: r.style.type === "icon" ? r.style.url : "",
                              size: r.style.type === "icon" ? r.style.size : 24,
                            },
                          }
                    );
                  }}
                >
                  <option value="color">{t("editor.markerRule.styleColor")}</option>
                  <option value="icon">{t("editor.markerRule.styleIcon")}</option>
                </select>
              </label>
              {styleType === "color" ? (
                <label>
                  <WidgetEditorCaption text={t("editor.markerRule.color")} />
                  <input
                    type="color"
                    value={
                      rule.style.type === "color" && /^#[0-9a-fA-F]{6}$/.test(rule.style.color)
                        ? rule.style.color
                        : "#3fb950"
                    }
                    onChange={(e) =>
                      patch(index, (r) => ({
                        ...r,
                        style: { type: "color", color: e.target.value },
                      }))
                    }
                  />
                  <input
                    value={rule.style.type === "color" ? rule.style.color : "#3fb950"}
                    onChange={(e) =>
                      patch(index, (r) => ({
                        ...r,
                        style: { type: "color", color: e.target.value },
                      }))
                    }
                  />
                </label>
              ) : (
                <>
                  <WidgetMediaUploadField
                    label={t("editor.markerRule.iconUrl")}
                    value={rule.style.type === "icon" ? rule.style.url : ""}
                    onChange={(url) =>
                      patch(index, (r) => ({
                        ...r,
                        style: {
                          type: "icon",
                          url,
                          size: r.style.type === "icon" ? r.style.size : 24,
                        },
                      }))
                    }
                    accept="image/png,image/jpeg,image/webp,image/gif,image/svg+xml,.svg"
                    placeholder={t("editor.markerRule.iconUrlHint")}
                    previewAlt={t("editor.markerRule.styleIcon")}
                  />
                  <label>
                    <WidgetEditorCaption text={t("editor.markerRule.iconSize")} />
                    <input
                      type="number"
                      min={12}
                      max={96}
                      value={rule.style.type === "icon" ? (rule.style.size ?? 24) : 24}
                      onChange={(e) =>
                        patch(index, (r) => ({
                          ...r,
                          style: {
                            type: "icon",
                            url: r.style.type === "icon" ? r.style.url : "",
                            size: Number(e.target.value) || 24,
                          },
                        }))
                      }
                    />
                  </label>
                </>
              )}
            </div>
          </div>
        );
      })}
      <button type="button" onClick={() => commit([...rules, newMarkerRule()])}>
        {t("editor.markerRule.add")}
      </button>
    </div>
  );
}
