import { useEffect, useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import {
  applyAnalyticsTemplate,
  deleteAnalyticsTemplate,
  fetchAnalyticsTemplateByPath,
  fetchObjects,
  updateAnalyticsTemplate,
  type AnalyticsTemplateDto,
} from "../../api";
import { AnalyticsTemplatePreview } from "./AnalyticsTemplatePreview";
import ObjectFederationBindSection from "../ObjectFederationBindSection";

const BUILTIN_TEMPLATE_IDS = new Set(["rollingAvg", "rateOfChange", "oee"]);
const WINDOW_BUCKETS = ["1m", "5m", "15m", "1h", "8h", "1d"];
const HELPERS = ["rollingAvg", "rateOfChange", "oee"];
const BLUEPRINT_BY_HELPER: Record<string, string> = {
  rollingAvg: "rolling-avg-v1",
  rateOfChange: "rate-of-change-v1",
  oee: "oee-v1",
};

interface AnalyticsTemplateInspectorProps {
  path: string;
  canManage?: boolean;
  onDeleted?: () => void;
}

export default function AnalyticsTemplateInspector({
  path,
  canManage = false,
  onDeleted,
}: AnalyticsTemplateInspectorProps) {
  const { t } = useTranslation(["automation", "common"]);
  const queryClient = useQueryClient();
  const [draft, setDraft] = useState<AnalyticsTemplateDto | null>(null);
  const [applyDevicePath, setApplyDevicePath] = useState("");
  const [applySourcePath, setApplySourcePath] = useState("");
  const [applySourceVariable, setApplySourceVariable] = useState("");
  const [applyAvailabilityVariable, setApplyAvailabilityVariable] = useState("");
  const [applyPerformanceVariable, setApplyPerformanceVariable] = useState("");
  const [applyQualityVariable, setApplyQualityVariable] = useState("");

  const templateQuery = useQuery({
    queryKey: ["analytics-template", path],
    queryFn: () => fetchAnalyticsTemplateByPath(path),
  });

  const devicesQuery = useQuery({
    queryKey: ["analytics-apply-devices"],
    queryFn: () => fetchObjects("root.platform.devices"),
    staleTime: 60_000,
  });

  useEffect(() => {
    if (templateQuery.data) {
      setDraft(templateQuery.data);
      setApplySourcePath(templateQuery.data.sourcePath);
      setApplySourceVariable(templateQuery.data.sourceVariable);
    }
  }, [templateQuery.data]);

  const builtIn = draft ? BUILTIN_TEMPLATE_IDS.has(draft.templateId) : false;
  const isOee = draft?.helper === "oee" || draft?.blueprintName === "oee-v1";

  const deviceOptions = useMemo(
    () => (devicesQuery.data ?? []).filter((object) => object.type === "DEVICE"),
    [devicesQuery.data],
  );

  const saveMutation = useMutation({
    mutationFn: (payload: AnalyticsTemplateDto) => updateAnalyticsTemplate(path, payload),
    onSuccess: (saved) => {
      setDraft(saved);
      queryClient.invalidateQueries({ queryKey: ["analytics-template", path] });
      queryClient.invalidateQueries({ queryKey: ["variables", path] });
      queryClient.invalidateQueries({ queryKey: ["objects"] });
    },
  });

  const deleteMutation = useMutation({
    mutationFn: () => deleteAnalyticsTemplate(path),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["objects"] });
      onDeleted?.();
    },
  });

  const applyMutation = useMutation({
    mutationFn: () =>
      applyAnalyticsTemplate({
        templatePath: path,
        devicePath: applyDevicePath,
        sourcePath: applySourcePath || undefined,
        sourceVariable: applySourceVariable,
        sourceField: draft?.sourceField || "value",
        windowBucket: draft?.windowBucket,
        availabilityVariable: isOee ? applyAvailabilityVariable : undefined,
        performanceVariable: isOee ? applyPerformanceVariable : undefined,
        qualityVariable: isOee ? applyQualityVariable : undefined,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["objects"] });
      queryClient.invalidateQueries({ queryKey: ["variables"] });
    },
  });

  if (templateQuery.isLoading || !draft) {
    return <p className="hint">{t("automation:analyticsTemplate.loading")}</p>;
  }

  if (templateQuery.error) {
    return <p className="hint error">{String(templateQuery.error)}</p>;
  }

  function patchDraft(patch: Partial<AnalyticsTemplateDto>) {
    setDraft((current) => (current ? { ...current, ...patch } : current));
  }

  function handleHelperChange(helper: string) {
    patchDraft({
      helper,
      blueprintName: BLUEPRINT_BY_HELPER[helper] ?? draft?.blueprintName ?? "",
    });
  }

  return (
    <section className="automation-inspector analytics-template-inspector">
      <header className="automation-panel-head">
        <div>
          <h3>{t("automation:analyticsTemplate.title")}</h3>
          <p className="hint">{t("automation:analyticsTemplate.subtitle")}</p>
          <p className="hint">
            <code>{path}</code>
          </p>
        </div>
      </header>

      <form
        className="form-grid"
        onSubmit={(event) => {
          event.preventDefault();
          if (!canManage || !draft) {
            return;
          }
          saveMutation.mutate({
            ...draft,
            path,
          });
        }}
      >
        <label>
          {t("automation:analyticsTemplate.templateId")}
          <input
            value={draft.templateId}
            onChange={(event) => patchDraft({ templateId: event.target.value })}
            required
            readOnly={!canManage || builtIn}
          />
        </label>
        <label>
          {t("automation:analyticsTemplate.helper")}
          <select
            value={draft.helper}
            onChange={(event) => handleHelperChange(event.target.value)}
            disabled={!canManage || builtIn}
          >
            {HELPERS.map((helper) => (
              <option key={helper} value={helper}>
                {helper}
              </option>
            ))}
          </select>
        </label>
        <label className="full">
          {t("automation:analyticsTemplate.displayName")}
          <input
            value={draft.displayName}
            onChange={(event) => patchDraft({ displayName: event.target.value })}
            readOnly={!canManage}
          />
        </label>
        <label className="full">
          {t("automation:analyticsTemplate.description")}
          <textarea
            value={draft.description}
            onChange={(event) => patchDraft({ description: event.target.value })}
            rows={2}
            readOnly={!canManage}
          />
        </label>
        <label className="full">
          {t("automation:analyticsTemplate.sourcePath")}
          <input
            value={draft.sourcePath}
            onChange={(event) => patchDraft({ sourcePath: event.target.value })}
            placeholder="root.platform.devices.demo-sensor-01"
            readOnly={!canManage}
          />
        </label>
        <label>
          {t("automation:analyticsTemplate.sourceVariable")}
          <input
            value={draft.sourceVariable}
            onChange={(event) => patchDraft({ sourceVariable: event.target.value })}
            placeholder="temperature"
            readOnly={!canManage}
          />
        </label>
        <label>
          {t("automation:analyticsTemplate.sourceField")}
          <input
            value={draft.sourceField}
            onChange={(event) => patchDraft({ sourceField: event.target.value })}
            readOnly={!canManage}
          />
        </label>
        <label>
          {t("automation:analyticsTemplate.windowBucket")}
          <select
            value={draft.windowBucket}
            onChange={(event) => patchDraft({ windowBucket: event.target.value })}
            disabled={!canManage}
          >
            {WINDOW_BUCKETS.map((bucket) => (
              <option key={bucket} value={bucket}>
                {bucket}
              </option>
            ))}
          </select>
        </label>
        <label>
          {t("automation:analyticsTemplate.blueprintName")}
          <input
            value={draft.blueprintName}
            onChange={(event) => patchDraft({ blueprintName: event.target.value })}
            readOnly={!canManage || builtIn}
          />
        </label>
        <label className="checkbox">
          <input
            type="checkbox"
            checked={draft.enabled}
            onChange={(event) => patchDraft({ enabled: event.target.checked })}
            disabled={!canManage}
          />
          {t("automation:analyticsTemplate.enabled")}
        </label>

        {canManage && (
          <div className="form-actions full">
            <button type="submit" className="btn primary" disabled={saveMutation.isPending}>
              {t("common:action.save")}
            </button>
            {!builtIn && (
              <button
                type="button"
                className="btn danger"
                disabled={deleteMutation.isPending}
                onClick={() => {
                  if (window.confirm(t("automation:analyticsTemplate.deleteConfirm"))) {
                    deleteMutation.mutate();
                  }
                }}
              >
                {t("common:action.delete")}
              </button>
            )}
          </div>
        )}
        {saveMutation.error && <p className="hint error full">{String(saveMutation.error)}</p>}
        {deleteMutation.error && <p className="hint error full">{String(deleteMutation.error)}</p>}
      </form>

      <section className="analytics-template-preview-section">
        <h4>{t("automation:analyticsTemplate.previewTitle")}</h4>
        <AnalyticsTemplatePreview template={draft} />
      </section>

      <section className="analytics-template-apply-section">
        <h4>{t("automation:analyticsTemplate.applyTitle")}</h4>
        <p className="hint">{t("automation:analyticsTemplate.applyHint")}</p>
        <div className="form-grid">
          <label className="full">
            {t("automation:analyticsTemplate.devicePath")}
            <select
              value={applyDevicePath}
              onChange={(event) => setApplyDevicePath(event.target.value)}
              disabled={!canManage}
            >
              <option value="">{t("automation:analyticsTemplate.selectDevice")}</option>
              {deviceOptions.map((device) => (
                <option key={device.path} value={device.path}>
                  {device.displayName || device.path}
                </option>
              ))}
            </select>
          </label>
          <label className="full">
            {t("automation:analyticsTemplate.sourcePath")}
            <input
              value={applySourcePath}
              onChange={(event) => setApplySourcePath(event.target.value)}
              placeholder={applyDevicePath || "root.platform.devices.*"}
              readOnly={!canManage}
            />
          </label>
          <label>
            {t("automation:analyticsTemplate.sourceVariable")}
            <input
              value={applySourceVariable}
              onChange={(event) => setApplySourceVariable(event.target.value)}
              required
              readOnly={!canManage}
            />
          </label>
          {isOee && (
            <>
              <label>
                {t("automation:analyticsTemplate.availabilityVariable")}
                <input
                  value={applyAvailabilityVariable}
                  onChange={(event) => setApplyAvailabilityVariable(event.target.value)}
                  readOnly={!canManage}
                />
              </label>
              <label>
                {t("automation:analyticsTemplate.performanceVariable")}
                <input
                  value={applyPerformanceVariable}
                  onChange={(event) => setApplyPerformanceVariable(event.target.value)}
                  readOnly={!canManage}
                />
              </label>
              <label>
                {t("automation:analyticsTemplate.qualityVariable")}
                <input
                  value={applyQualityVariable}
                  onChange={(event) => setApplyQualityVariable(event.target.value)}
                  readOnly={!canManage}
                />
              </label>
            </>
          )}
          {canManage && (
            <div className="form-actions full">
              <button
                type="button"
                className="btn primary"
                disabled={
                  applyMutation.isPending || !applyDevicePath || !applySourceVariable.trim()
                  || (isOee && (
                    !applyAvailabilityVariable.trim()
                    || !applyPerformanceVariable.trim()
                    || !applyQualityVariable.trim()
                  ))
                }
                onClick={() => applyMutation.mutate()}
              >
                {t("automation:analyticsTemplate.applyButton")}
              </button>
            </div>
          )}
          {applyMutation.data && (
            <p className="hint full">
              {t("automation:analyticsTemplate.applyResult", {
                device: applyMutation.data.devicePath,
                status: applyMutation.data.refresh.status,
                message: applyMutation.data.refresh.message,
              })}
            </p>
          )}
          {applyMutation.error && <p className="hint error full">{String(applyMutation.error)}</p>}
        </div>
      </section>

      <ObjectFederationBindSection path={path} canManage={canManage} />
    </section>
  );
}
