import { useQuery } from "@tanstack/react-query";
import { useMemo } from "react";
import { useTranslation } from "react-i18next";
import { fetchObjectEditor } from "../../api";
import type { ObjectEditorDto } from "../../types";
import { inputMapIssue, payloadJsonIssue, type BpmnJsonIssue } from "./bpmnIspfValue";

type DescriptorKind = "functions" | "variables" | "events";

function descriptorNames(data: ObjectEditorDto, kind: DescriptorKind): string[] {
  const raw =
    kind === "functions"
      ? (data.functions ?? []).map((item) => item.name)
      : kind === "variables"
        ? (data.variables ?? []).map((item) => item.name)
        : (data.events ?? []).map((item) => item.name);
  return [...new Set(raw.map((name) => name.trim()).filter(Boolean))].sort((a, b) =>
    a.localeCompare(b),
  );
}

const EMPTY_KEY: Record<DescriptorKind, string> = {
  functions: "bpmn.props.descriptorEmptyFunctions",
  variables: "bpmn.props.descriptorEmptyVariables",
  events: "bpmn.props.descriptorEmptyEvents",
};

export function BpmnDescriptorSelect({
  label,
  kind,
  objectPath,
  value,
  onChange,
}: {
  label: string;
  kind: DescriptorKind;
  objectPath: string;
  value: string;
  onChange: (next: string) => void;
}) {
  const { t } = useTranslation("workflow");
  const path = objectPath.trim();
  const query = useQuery({
    queryKey: ["bpmn-descriptor", path],
    queryFn: () => fetchObjectEditor(path),
    enabled: Boolean(path),
  });
  const names = useMemo(
    () => (query.data ? descriptorNames(query.data, kind) : []),
    [query.data, kind],
  );
  const current = value.trim();
  const options = current && !names.includes(current) ? [current, ...names] : names;

  return (
    <label className="field-block">
      <span className="field-caption">{label}</span>
      <select
        aria-label={label}
        value={current}
        disabled={!path || query.isLoading}
        onChange={(event) => onChange(event.target.value)}
      >
        <option value="">—</option>
        {options.map((name) => (
          <option key={name} value={name}>
            {name}
          </option>
        ))}
      </select>
      {!path ? <span className="hint">{t("bpmn.props.descriptorNeedObject")}</span> : null}
      {path && query.isSuccess && names.length === 0 ? (
        <span className="hint">{t(EMPTY_KEY[kind])}</span>
      ) : null}
    </label>
  );
}

export function BpmnCheckedText({
  label,
  kind,
  value,
  onChange,
}: {
  label: string;
  kind: "inputMap" | "payloadJson";
  value: string;
  onChange: (next: string) => void;
}) {
  const { t } = useTranslation("workflow");
  const issue = kind === "inputMap" ? inputMapIssue(value) : payloadJsonIssue(value);
  return (
    <label className="field-block">
      <span className="field-caption">{label}</span>
      <textarea
        aria-label={label}
        rows={3}
        value={value}
        spellCheck={false}
        onChange={(event) => onChange(event.target.value)}
      />
      {kind === "inputMap" ? <span className="hint">{t("bpmn.props.inputMapHint")}</span> : null}
      {issue ? (
        <span className="hint" role="alert">
          {t(issueKey(kind, issue))}
        </span>
      ) : null}
    </label>
  );
}

function issueKey(kind: "inputMap" | "payloadJson", issue: BpmnJsonIssue): string {
  if (kind === "inputMap" && issue === "pairs") {
    return "bpmn.props.inputMapInvalid";
  }
  if (issue === "json") {
    return kind === "inputMap" ? "bpmn.props.inputMapJsonInvalid" : "bpmn.props.payloadJsonInvalid";
  }
  return kind === "inputMap" ? "bpmn.props.inputMapShape" : "bpmn.props.payloadJsonShape";
}
