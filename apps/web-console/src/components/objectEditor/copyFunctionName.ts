import type { FunctionDescriptor } from "../../types";
import { normalizeFunctionDescriptor } from "../../utils/schema/dataSchema";

const CODE = /^[A-Za-z_][A-Za-z0-9_]*$/;

export function suggestedFunctionCopyName(name: string): string {
  const candidate = `${name}Copy`;
  return CODE.test(candidate) ? candidate : "";
}

/** Same descriptor under a new name. Input and output schema names stay as stored. */
export function functionCopyDescriptor(source: FunctionDescriptor, name: string): FunctionDescriptor {
  return normalizeFunctionDescriptor({
    ...source,
    name: name.trim(),
    description: source.description ?? "",
    invokeRoles: source.invokeRoles ?? [],
  });
}
