import type { FunctionDescriptor } from "../../types";

/** Whether the console should allow invoke (matches server dispatch, not only script/java). */
export function functionHasImplementation(fn: FunctionDescriptor): boolean {
  const body = fn.sourceBody?.trim() ?? "";
  const type = (fn.sourceType ?? "").trim().toLowerCase();

  if (type === "java" || type === "script" || type === "expression" || type === "object-query" || type === "pulse") {
    return body.length > 0;
  }
  if (body.length > 0) {
    return true;
  }
  return true;
}
