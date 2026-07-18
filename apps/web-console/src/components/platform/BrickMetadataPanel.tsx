import { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { fetchBrickInfer, toSuggestionDto, type BrickClassSuggestionDto } from "../../api/brickInfer";
import { fetchVariables, setVariable } from "../../api";
import { parseHaystackTagsJson } from "../../constants/haystackMarkers";
import type { DataRecord, VariableDto } from "../../types";
import { inferBrickClassHints } from "../../utils/object/brickClassHints";

interface BrickMetadataPanelProps {
  devicePath: string;
  canManage: boolean;
}

function variableString(variables: VariableDto[] | undefined, name: string): string {
  const variable = variables?.find((v) => v.name === name);
  const row = variable?.value?.rows?.[0] as Record<string, unknown> | undefined;
  if (!row) {
    return "";
  }
  const value = row.value;
  return value === null || value === undefined ? "" : String(value);
}

function stringRecord(value: string): DataRecord {
  return {
    schema: {
      name: "stringValue",
      fields: [{ name: "value", type: "STRING" }],
    },
    rows: [{ value }],
  };
}

function confidenceClass(confidence: string): string {
  switch (confidence) {
    case "high":
      return "brick-confidence-high";
    case "medium":
      return "brick-confidence-medium";
    default:
      return "brick-confidence-low";
  }
}

export default function BrickMetadataPanel({ devicePath, canManage }: BrickMetadataPanelProps) {
  const { t } = useTranslation(["inspector", "common"]);
  const queryClient = useQueryClient();
  const [brickClass, setBrickClass] = useState("");
  const [formError, setFormError] = useState<string | null>(null);

  const variablesQuery = useQuery({
    queryKey: ["variables", devicePath],
    queryFn: () => fetchVariables(devicePath),
  });

  const hasBrickMixin = useMemo(
    () => variablesQuery.data?.some((variable) => variable.name === "brickClass") ?? false,
    [variablesQuery.data]
  );

  const inferQuery = useQuery({
    queryKey: ["brick-infer", devicePath],
    queryFn: () => fetchBrickInfer(devicePath),
    enabled: hasBrickMixin,
    retry: false,
  });

  const fallbackSuggestions = useMemo(() => {
    if (!variablesQuery.data) {
      return [];
    }
    return inferBrickClassHints({
      haystackTags: parseHaystackTagsJson(variableString(variablesQuery.data, "haystackTags")),
      haystackKind: variableString(variablesQuery.data, "haystackKind"),
      displayName: inferQuery.data?.displayName,
      objectPath: devicePath,
    }).map((item): BrickClassSuggestionDto => ({
      brickClass: item.brickClass,
      compactClass: item.compactClass,
      confidence: item.confidence,
      reason: item.reason,
    }));
  }, [devicePath, inferQuery.data?.displayName, variablesQuery.data]);

  const suggestions = useMemo((): BrickClassSuggestionDto[] => {
    if (inferQuery.data) {
      return [toSuggestionDto(inferQuery.data)];
    }
    return fallbackSuggestions;
  }, [fallbackSuggestions, inferQuery.data]);

  useEffect(() => {
    if (!variablesQuery.data) {
      return;
    }
    setBrickClass(variableString(variablesQuery.data, "brickClass"));
    setFormError(null);
  }, [variablesQuery.data]);

  const saveMutation = useMutation({
    mutationFn: async () => {
      await setVariable(devicePath, "brickClass", stringRecord(brickClass.trim()));
    },
    onSuccess: async () => {
      setFormError(null);
      await queryClient.invalidateQueries({ queryKey: ["variables", devicePath] });
      await queryClient.invalidateQueries({ queryKey: ["brick-infer", devicePath] });
    },
    onError: (error: Error) => {
      setFormError(error.message);
    },
  });

  if (variablesQuery.isLoading) {
    return <p className="hint">{t("brick.loading")}</p>;
  }

  if (!hasBrickMixin) {
    return <p className="hint">{t("brick.mixinMissing")}</p>;
  }

  function applySuggestion(suggestion: BrickClassSuggestionDto) {
    setBrickClass(suggestion.brickClass);
  }

  const currentNormalized = brickClass.trim();
  const visibleSuggestions = suggestions.filter(
    (item) => item.brickClass.trim() !== currentNormalized
  );

  return (
    <div className="brick-metadata-panel">
      <p className="hint brick-metadata-intro">{t("brick.intro")}</p>

      <div className="property-fields">
        <label className="full">
          {t("brick.classUri")}
          <input
            type="text"
            value={brickClass}
            disabled={!canManage}
            placeholder="https://brickschema.org/schema/Brick#Sensor"
            onChange={(event) => setBrickClass(event.target.value)}
          />
        </label>
      </div>

      <section className="brick-suggestions">
        <h3 className="brick-suggestions-title">{t("brick.suggestions")}</h3>
        {inferQuery.isLoading && <p className="hint">{t("brick.inferLoading")}</p>}
        {inferQuery.isError && !inferQuery.isLoading && (
          <p className="hint">{t("brick.inferFallback")}</p>
        )}
        {visibleSuggestions.length === 0 ? (
          <p className="hint">{t("brick.noSuggestions")}</p>
        ) : (
          <ul className="brick-suggestion-list">
            {visibleSuggestions.map((item) => (
              <li key={item.brickClass} className="brick-suggestion-item">
                <div className="brick-suggestion-main">
                  <code className="brick-suggestion-class">{item.compactClass || item.brickClass}</code>
                  <span className={`badge brick-confidence ${confidenceClass(item.confidence)}`}>
                    {t(`brick.confidence.${item.confidence}`, item.confidence)}
                  </span>
                </div>
                <p className="hint brick-suggestion-reason">{item.reason}</p>
                {canManage && (
                  <button
                    type="button"
                    className="btn btn-sm"
                    onClick={() => applySuggestion(item)}
                  >
                    {t("brick.applySuggestion")}
                  </button>
                )}
              </li>
            ))}
          </ul>
        )}
      </section>

      {formError && <div className="banner error">{formError}</div>}

      {canManage && (
        <div className="panel-toolbar">
          <button
            type="button"
            className="btn btn-primary"
            disabled={saveMutation.isPending}
            onClick={() => saveMutation.mutate()}
          >
            {saveMutation.isPending ? t("common:action.saving") : t("brick.save")}
          </button>
        </div>
      )}
    </div>
  );
}
