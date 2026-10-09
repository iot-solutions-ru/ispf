import type { BlueprintOwnershipDto } from "../../types";

export type BlueprintOwnedKind = keyof BlueprintOwnershipDto;

export function isBlueprintOwned(
  ownership: BlueprintOwnershipDto | null | undefined,
  kind: BlueprintOwnedKind,
  name: string,
): boolean {
  return Boolean(ownership?.[kind]?.includes(name));
}
