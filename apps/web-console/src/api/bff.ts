import { getAuthHeaders } from "../auth/session";
import { fetchWithIngressFallback } from "../utils/ingressFetch";
import {
  ISPF_OPERATOR_WIRE_PROFILE,
  type BffInvokeRequest,
  type BffWireResponse,
  isBffOk,
} from "../types/bff";

export async function bffInvoke<T = unknown>(request: BffInvokeRequest): Promise<BffWireResponse<T>> {
  const response = await fetchWithIngressFallback("/api/v1/bff/invoke", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      ...getAuthHeaders(),
    },
    body: JSON.stringify({
      ...request,
      wireProfile: request.wireProfile ?? ISPF_OPERATOR_WIRE_PROFILE,
    }),
  });
  if (!response.ok) {
    const text = await response.text();
    throw new Error(text || `BFF invoke failed: ${response.status}`);
  }
  return response.json();
}

export function assertBffOk<T>(wire: BffWireResponse<T>): T {
  if (!isBffOk(wire)) {
    throw new Error(wire.error_message || wire.error_code || "BFF error");
  }
  return wire.result;
}

export type BffFieldType = "STRING" | "DOUBLE" | "BOOLEAN" | "LONG" | "INTEGER";

export function toBffInput(
  values: Record<string, unknown> | undefined,
  fieldTypes?: Record<string, BffFieldType>,
  schemaName = "in"
): NonNullable<BffInvokeRequest["input"]> {
  const row = values ?? {};
  const fields = Object.keys(row).map((name) => ({
    name,
    type: fieldTypes?.[name] ?? inferBffFieldType(row[name]),
  }));
  return {
    schema: { name: schemaName, fields },
    rows: [row],
  };
}

function inferBffFieldType(value: unknown): BffFieldType {
  if (typeof value === "boolean") {
    return "BOOLEAN";
  }
  if (typeof value === "number") {
    return Number.isInteger(value) ? "LONG" : "DOUBLE";
  }
  return "STRING";
}
