import { fetchObjects } from "../../api";
import type { ObjectSummary } from "../../types";
import { DATA_SOURCES_ROOT } from "./platformSqlPath";

/** Lists every readable DATA_SOURCE under the platform data-sources catalog; VISUAL_GROUP nodes are skipped. */
export async function loadDataSourceOptions(
  rootPath: string = DATA_SOURCES_ROOT,
): Promise<ObjectSummary[]> {
  const sources: ObjectSummary[] = [];

  async function walk(parentPath: string): Promise<void> {
    const children = await fetchObjects(parentPath);
    const nestedGroups: string[] = [];

    for (const node of children) {
      if (node.type === "DATA_SOURCE") {
        sources.push(node);
      } else if (node.type === "VISUAL_GROUP") {
        nestedGroups.push(node.path);
      }
    }

    if (nestedGroups.length > 0) {
      await Promise.all(nestedGroups.map((groupPath) => walk(groupPath)));
    }
  }

  await walk(rootPath);

  const byPath = new Map<string, ObjectSummary>();
  for (const source of sources) {
    byPath.set(source.path, source);
  }
  return [...byPath.values()].sort((left, right) => left.path.localeCompare(right.path));
}
