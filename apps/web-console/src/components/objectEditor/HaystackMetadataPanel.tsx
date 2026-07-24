import { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Alert, Button, Checkbox, Input, Select, Space, Tag } from "antd";
import { fetchVariables, setVariable } from "../../api";
import {
  COMMON_HAYSTACK_MARKERS,
  parseHaystackTagsJson,
  serializeHaystackTags,
} from "../../constants/haystackMarkers";
import type { DataRecord, VariableDto } from "../../types";

interface HaystackMetadataPanelProps {
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

export default function HaystackMetadataPanel({ devicePath, canManage }: HaystackMetadataPanelProps) {
  const { t } = useTranslation(["inspector", "common"]);
  const queryClient = useQueryClient();
  const [selectedTags, setSelectedTags] = useState<string[]>([]);
  const [haystackRef, setHaystackRef] = useState("");
  const [haystackKind, setHaystackKind] = useState("");
  const [customTag, setCustomTag] = useState("");
  const [formError, setFormError] = useState<string | null>(null);

  const variablesQuery = useQuery({
    queryKey: ["variables", devicePath],
    queryFn: () => fetchVariables(devicePath),
  });

  const hasHaystackMixin = useMemo(
    () => variablesQuery.data?.some((variable) => variable.name === "haystackTags") ?? false,
    [variablesQuery.data]
  );

  useEffect(() => {
    if (!variablesQuery.data) {
      return;
    }
    setSelectedTags(parseHaystackTagsJson(variableString(variablesQuery.data, "haystackTags")));
    setHaystackRef(variableString(variablesQuery.data, "haystackRef"));
    setHaystackKind(variableString(variablesQuery.data, "haystackKind"));
    setFormError(null);
  }, [variablesQuery.data]);

  const saveMutation = useMutation({
    mutationFn: async () => {
      const tagsJson = serializeHaystackTags(selectedTags);
      await setVariable(devicePath, "haystackTags", stringRecord(tagsJson));
      await setVariable(devicePath, "haystackRef", stringRecord(haystackRef.trim()));
      await setVariable(devicePath, "haystackKind", stringRecord(haystackKind.trim()));
    },
    onSuccess: async () => {
      setFormError(null);
      await queryClient.invalidateQueries({ queryKey: ["variables", devicePath] });
    },
    onError: (error: Error) => {
      setFormError(error.message);
    },
  });

  if (variablesQuery.isLoading) {
    return <p className="hint">{t("haystack.loading")}</p>;
  }

  if (!hasHaystackMixin) {
    return <p className="hint">{t("haystack.mixinMissing")}</p>;
  }

  function toggleTag(tag: string) {
    setSelectedTags((current) =>
      current.includes(tag) ? current.filter((item) => item !== tag) : [...current, tag]
    );
  }

  function addCustomTag() {
    const trimmed = customTag.trim();
    if (!trimmed) {
      return;
    }
    setSelectedTags((current) => (current.includes(trimmed) ? current : [...current, trimmed]));
    setCustomTag("");
  }

  const customTags = selectedTags.filter(
    (tag) => !(COMMON_HAYSTACK_MARKERS as readonly string[]).includes(tag)
  );

  return (
    <div className="haystack-metadata-panel">
      <p className="hint haystack-metadata-intro">{t("haystack.intro")}</p>

      <fieldset className="function-form-multiselect haystack-marker-fieldset" disabled={!canManage}>
        <legend>{t("haystack.tags")}</legend>
        <div className="function-form-multiselect-options">
          {COMMON_HAYSTACK_MARKERS.map((tag) => (
            <label key={tag} className="function-form-multiselect-option">
              <Checkbox
                checked={selectedTags.includes(tag)}
                disabled={!canManage}
                onChange={() => toggleTag(tag)}
              />
              {t(`haystack.marker.${tag}`, tag)}
            </label>
          ))}
        </div>
      </fieldset>

      {customTags.length > 0 && (
        <div className="haystack-custom-tags">
          <span className="haystack-custom-tags-label">{t("haystack.customTags")}</span>
          <div className="haystack-custom-tags-list">
            {customTags.map((tag) => (
              <Tag
                key={tag}
                closable={canManage}
                onClose={(event) => {
                  event.preventDefault();
                  toggleTag(tag);
                }}
              >
                {tag}
              </Tag>
            ))}
          </div>
        </div>
      )}

      {canManage && (
        <div className="haystack-custom-tag-add property-fields">
          <label className="full">
            {t("haystack.addCustomTag")}
            <div className="haystack-custom-tag-row">
              <Input
                value={customTag}
                placeholder={t("haystack.customTagPlaceholder")}
                onChange={(event) => setCustomTag(event.target.value)}
                onKeyDown={(event) => {
                  if (event.key === "Enter") {
                    event.preventDefault();
                    addCustomTag();
                  }
                }}
              />
              <Button size="small" onClick={addCustomTag}>
                {t("common:action.add")}
              </Button>
            </div>
          </label>
        </div>
      )}

      <div className="property-fields">
        <label className="full">
          {t("haystack.kind")}
          <Select
            value={haystackKind}
            disabled={!canManage}
            onChange={setHaystackKind}
            options={[
              { value: "", label: t("haystack.kindNone") },
              { value: "equip", label: t("haystack.kindEquip") },
              { value: "point", label: t("haystack.kindPoint") },
              { value: "site", label: t("haystack.kindSite") },
            ]}
          />
        </label>
        <label className="full">
          {t("haystack.ref")}
          <Input
            value={haystackRef}
            disabled={!canManage}
            placeholder="@site.equip"
            onChange={(event) => setHaystackRef(event.target.value)}
          />
        </label>
      </div>

      {formError && <Alert type="error" message={formError} showIcon />}

      {canManage && (
        <Space className="panel-toolbar">
          <Button type="primary" loading={saveMutation.isPending} onClick={() => saveMutation.mutate()}>
            {saveMutation.isPending ? t("common:action.saving") : t("haystack.save")}
          </Button>
        </Space>
      )}
    </div>
  );
}
