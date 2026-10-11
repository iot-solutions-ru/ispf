import type { ObjectType } from "../../types";

/**
 * Path-prefix helpers (data-sources.*, alert-rules.*, …) also match VISUAL_GROUP
 * nodes under those catalogs. Typed inspectors must not open for groups.
 */
export function isTypedCatalogObject(
  type: ObjectType | undefined,
  pathMatchesTypedObject: boolean,
): boolean {
  return type !== "VISUAL_GROUP" && pathMatchesTypedObject;
}

/** Catalog roots and VISUAL_GROUP nodes share the child-list panel. */
export function opensCatalogChildList(
  type: ObjectType | undefined,
  isCatalogFolder: boolean,
): boolean {
  return type === "VISUAL_GROUP" || isCatalogFolder;
}
