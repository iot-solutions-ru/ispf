import { useEffect, useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import {
  applyAnalyticsTemplate,
  fetchAnalyticsTemplates,
  updateVariableHistory,
  type AnalyticsTemplateDto,
} from "../../api";

const DERIVED_OR_META = new Set([
  "derivedValue",
  "oeePct",
  "analyticsHelper",
  "analyticsExpression",
  "sourcePath",
  "sourceVariable",
  "sourceField",
  "windowBucket",
  "availabilityVariable",
  "performanceVariable",
  "qualityVariable",
]);

const PREFERRED_SOURCE = ["temperature", "value", "pressure", "flow", "level"];

function guessSourceVariable(variableNames: string[], template?: AnalyticsTemplateDto): string {
  const candidates = variableNames.filter((name) => !DERIVED_OR_META.has(name));
  for (const preferred of PREFERRED_SOURCE) {
    if (candidates.includes(preferred)) {
      return preferred;
    }
  }
  if (template?.sourceVariable && candidates.includes(template.sourceVariable)) {
    return template.sourceVariable;
  }
  return candidates[0] ?? "";
}

function defaultTemplate(templates: AnalyticsTemplateDto[]): AnalyticsTemplateDto | null {
  const enabled = templates.filter((item) => item.enabled);
  return (
    enabled.find((item) => item.templateId === "rollingAvg")
    ?? enabled.find((item) => item.helper === "rollingAvg")
    ?? enabled[0]
    ?? null
  );
}

interface AnalyticsTagIncompleteSetupProps {
  devicePath: string;
  variableNames: string[];
  canManage: boolean;
}

export default function AnalyticsTagIncompleteSetup({
  devicePath,
  variableNames,
  canManage,
}: AnalyticsTagIncompleteSetupProps) {
  const { t } = useTranslation(["automation", "common"]);
  const queryClient = useQueryClient();
  const templatesQuery = useQuery({
    queryKey: ["analytics-templates"],
    queryFn: fetchAnalyticsTemplates,
    staleTime: 60_000,
  });

  const templates = useMemo(
    () => (templatesQuery.data ?? []).filter((item) => item.enabled),
    [templatesQuery.data],
  );

  const [templatePath, setTemplatePath] = useState("");
  const [sourceVariable, setSourceVariable] = useState("");
  const [enableHistorian, setEnableHistorian] = useState(true);
  const [message, setMessage] = useState<string | null>(null);

  const selectedTemplate = templates.find((item) => item.path === templatePath) ?? null;
  const isOee = selectedTemplate?.helper === "oee" || selectedTemplate?.blueprintName === "oee-v1";

  const sourceOptions = useMemo(
    () => variableNames.filter((name) => !DERIVED_OR_META.has(name)),
    [variableNames],
  );

  useEffect(() => {
    if (!templates.length) {
      return;
    }
    const initial = defaultTemplate(templates);
    if (!initial) {
      return;
    }
    setTemplatePath((current) => current || initial.path);
    setSourceVariable((current) => current || guessSourceVariable(variableNames, initial));
  }, [templates, variableNames]);

  useEffect(() => {
    if (!selectedTemplate || sourceVariable) {
      return;
    }
    setSourceVariable(guessSourceVariable(variableNames, selectedTemplate));
  }, [selectedTemplate, sourceVariable, variableNames]);

  const applyMutation = useMutation({
    mutationFn: async () => {
      if (!selectedTemplate) {
        throw new Error(t("automation:analyticsTag.setup.noTemplate"));
      }
      if (!sourceVariable.trim()) {
        throw new Error(t("automation:analyticsTag.setup.noSource"));
      }
      const sourcePath = selectedTemplate.sourcePath || devicePath;
      if (enableHistorian) {
        await updateVariableHistory(sourcePath, sourceVariable.trim(), {
          historyEnabled: true,
          historyRetentionDays: null,
        });
      }
      return applyAnalyticsTemplate({
        templatePath: selectedTemplate.path,
        devicePath,
        sourcePath: sourcePath !== devicePath ? sourcePath : undefined,
        sourceVariable: sourceVariable.trim(),
        sourceField: selectedTemplate.sourceField || "value",
        windowBucket: selectedTemplate.windowBucket,
      });
    },
    onSuccess: async () => {
      setMessage(t("automation:analyticsTag.setup.applied"));
      await queryClient.invalidateQueries({ queryKey: ["object-editor", devicePath] });
      await queryClient.invalidateQueries({ queryKey: ["variables", devicePath] });
      await queryClient.invalidateQueries({ queryKey: ["objects"] });
    },
    onError: (error: Error) => {
      setMessage(error.message);
    },
  });

  return (
    <div className="analytics-tag-incomplete-setup">
      <p className="hint">{t("automation:analyticsTag.incomplete")}</p>

      {canManage ? (
        <form
          className="analytics-tag-setup-form"
          onSubmit={(event) => {
            event.preventDefault();
            setMessage(null);
            applyMutation.mutate();
          }}
        >
          <label>
            <span>{t("automation:analyticsTag.setup.template")}</span>
            <select
              value={templatePath}
              disabled={templatesQuery.isLoading || !templates.length}
              onChange={(event) => setTemplatePath(event.target.value)}
            >
              {!templates.length && <option value="">{t("automation:analyticsTag.setup.noTemplates")}</option>}
              {templates.map((template) => (
                <option key={template.path} value={template.path}>
                  {template.displayName || template.templateId}
                </option>
              ))}
            </select>
          </label>

          <label>
            <span>{t("automation:analyticsTag.setup.sourceVariable")}</span>
            <select
              value={sourceVariable}
              disabled={!sourceOptions.length}
              onChange={(event) => setSourceVariable(event.target.value)}
            >
              {!sourceOptions.length && <option value="">{t("automation:analyticsTag.setup.noSource")}</option>}
              {sourceOptions.map((name) => (
                <option key={name} value={name}>
                  {name}
                </option>
              ))}
            </select>
          </label>

          <label className="analytics-tag-setup-checkbox">
            <input
              type="checkbox"
              checked={enableHistorian}
              onChange={(event) => setEnableHistorian(event.target.checked)}
            />
            <span>{t("automation:analyticsTag.setup.enableHistorian")}</span>
          </label>

          {isOee && (
            <p className="hint">{t("automation:analyticsTag.setup.oeeUseTemplatePage")}</p>
          )}

          <div className="analytics-tag-setup-actions">
            <button
              type="submit"
              className="btn primary"
              disabled={
                applyMutation.isPending
                || !templatePath
                || !sourceVariable.trim()
                || isOee
              }
            >
              {applyMutation.isPending
                ? t("automation:analyticsTag.setup.applying")
                : t("automation:analyticsTag.setup.apply")}
            </button>
          </div>

          {message && <p className={`hint${applyMutation.isError ? " error" : ""}`}>{message}</p>}
        </form>
      ) : (
        <p className="hint">{t("automation:analyticsTag.incompleteHint")}</p>
      )}
    </div>
  );
}
