import { fetchObjects } from "../../api";
import type { ObjectSummary } from "../../types";
import { DATA_SOURCES_ROOT } from "./platformSqlPath";

/** Lists every readable DATA_SOURCE under the platform data-sources catalog, including bundle visual groups. */
export async function loadDataSourceOptions(
  rootPath: string = DATA_SOURCES_ROOT,
): Promise<ObjectSummary[]> {
  const topLevel = await fetchObjects(rootPath);
  const sources: ObjectSummary[] = [];
  const groupFetches: Promise<ObjectSummary[]>[] = [];

  for (const node of topLevel) {
    if (node.type === "DATA_SOURCE") {
      sources.push(node);
      continue;
    }
    if (node.type === "VISUAL_GROUP") {
      groupFetches.push(fetchObjects(node.path));
    }
  }

  if (groupFetches.length > 0) {
    const memberLists = await Promise.all(groupFetches);
    for (const members of memberLists) {
      for (const member of members) {
        if (member.type === "DATA_SOURCE") {
          sources.push(member);
        }
      }
    }
  }

  const byPath = new Map<string, ObjectSummary>();
  for (const source of sources) {
    byPath.set(source.path, source);
  }
  return [...byPath.values()].sort((left, right) => left.path.localeCompare(right.path));
}
