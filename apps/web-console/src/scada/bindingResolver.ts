import type { DashboardSession } from "../components/dashboard/DashboardContext";
import { resolveWidgetPath } from "../components/dashboard/dashboardUtils";
import { readFieldValue } from "../types/dashboard";
import type { VariableDto } from "../types";
import type { MimicBinding, MimicConnection, MimicElement } from "../types/scadaMimic";
import { fieldsFromRef } from "../utils/platformRef";
import { asBool, asNum } from "./utils";

export interface ResolvedBindingValues {
  byElementId: Record<string, Record<string, unknown>>;
  byConnectionId: Record<string, Record<string, unknown>>;
}

export function collectBindingPaths(
  elements: MimicElement[],
  connections: MimicConnection[],
  session?: Pick<DashboardSession, "selection" | "params">
): string[] {
  return [...new Set(collectBindingInterests(elements, connections, session).map((entry) => entry.path))];
}

/** Live variable interests only (bindings) — action target paths are not WS live subscriptions. */
export function collectBindingInterests(
  elements: MimicElement[],
  connections: MimicConnection[],
  session?: Pick<DashboardSession, "selection" | "params">
): Array<{ path: string; variableName: string }> {
  const out: Array<{ path: string; variableName: string }> = [];
  const seen = new Set<string>();

  const addInterest = (binding: MimicBinding) => {
    const target = bindingTarget(binding, session ?? { selection: {}, params: {} });
    if (!target.objectPath?.trim() || !target.variableName?.trim()) {
      return;
    }
    const path = target.objectPath.trim();
    const variableName = target.variableName.trim();
    const key = `${path}\0${variableName}`;
    if (seen.has(key)) {
      return;
    }
    seen.add(key);
    out.push({ path, variableName });
  };

  for (const el of elements) {
    for (const binding of Object.values(el.bindings)) {
      addInterest(binding);
    }
  }
  for (const conn of connections) {
    for (const binding of Object.values(conn.bindings ?? {})) {
      if (binding) {
        addInterest(binding);
      }
    }
  }
  return out;
}

export function groupBindingVariablesByPath(
  interests: Array<{ path: string; variableName: string }>
): Record<string, string[]> {
  const byPath: Record<string, string[]> = {};
  for (const interest of interests) {
    const list = byPath[interest.path] ?? [];
    if (!list.includes(interest.variableName)) {
      list.push(interest.variableName);
    }
    byPath[interest.path] = list;
  }
  return byPath;
}

function bindingTarget(
  binding: MimicBinding,
  session: Pick<DashboardSession, "selection" | "params">
): { objectPath?: string; variableName?: string; valueField: string } {
  const fields = binding.ref ? fieldsFromRef(binding.ref) : null;
  const variableName = fields?.name ?? binding.variableName;
  const valueField = fields?.field ?? binding.valueField ?? "value";
  const pathHint = fields?.objectPath && fields.objectPath !== "self"
    ? fields.objectPath
    : binding.objectPath;
  const objectPath = resolveWidgetPath(
    pathHint,
    binding.selectionKey,
    session.selection,
    undefined,
    session.params
  );
  return { objectPath, variableName, valueField };
}

export function resolveBindingValue(
  binding: MimicBinding | undefined,
  session: Pick<DashboardSession, "selection" | "params">,
  variablesByPath: Record<string, VariableDto[] | undefined>
): unknown {
  if (!binding?.variableName && !binding?.ref) return undefined;
  const target = bindingTarget(binding, session);
  if (!target.objectPath || !target.variableName) return undefined;
  const variables = variablesByPath[target.objectPath];
  const variable = variables?.find((v) => v.name === target.variableName);
  const row = variable?.value?.rows?.[0];
  if (!row) return undefined;
  const raw = readFieldValue(row, target.valueField);
  return transformBindingValue(raw, binding.transform);
}

export function resolveBindingQuality(
  binding: MimicBinding | undefined,
  session: Pick<DashboardSession, "selection" | "params">,
  variablesByPath: Record<string, VariableDto[] | undefined>
): unknown {
  if (!binding?.qualityField?.trim()) return undefined;
  if (!binding?.variableName && !binding?.ref) return undefined;
  const target = bindingTarget(binding, session);
  if (!target.objectPath || !target.variableName) return undefined;
  const variables = variablesByPath[target.objectPath];
  const variable = variables?.find((v) => v.name === target.variableName);
  const row = variable?.value?.rows?.[0];
  if (!row) return undefined;
  return readFieldValue(row, binding.qualityField.trim());
}

function transformBindingValue(raw: unknown, transform?: MimicBinding["transform"]): unknown {
  if (!transform) return raw;
  switch (transform) {
    case "bool":
      return asBool(raw);
    case "number":
      return asNum(raw);
    case "string":
      return raw == null ? "" : String(raw);
    default:
      return raw;
  }
}

export function resolveDocumentBindings(
  elements: MimicElement[],
  connections: MimicConnection[],
  session: Pick<DashboardSession, "selection" | "params">,
  variablesByPath: Record<string, VariableDto[] | undefined>
): ResolvedBindingValues {
  const byElementId: Record<string, Record<string, unknown>> = {};
  for (const el of elements) {
    const values: Record<string, unknown> = {};
    for (const [key, binding] of Object.entries(el.bindings)) {
      values[key] = resolveBindingValue(binding, session, variablesByPath);
      if (binding.qualityField?.trim()) {
        values[`${key}Quality`] = resolveBindingQuality(binding, session, variablesByPath);
      }
    }
    byElementId[el.id] = values;
  }
  const byConnectionId: Record<string, Record<string, unknown>> = {};
  for (const conn of connections) {
    const values: Record<string, unknown> = {};
    if (conn.bindings?.flowing) {
      values.flowing = resolveBindingValue(conn.bindings.flowing, session, variablesByPath);
    }
    if (conn.bindings?.alarm) {
      values.alarm = resolveBindingValue(conn.bindings.alarm, session, variablesByPath);
    }
    byConnectionId[conn.id] = values;
  }
  return { byElementId, byConnectionId };
}
