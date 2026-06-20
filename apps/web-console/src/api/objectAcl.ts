import { getAuthHeaders } from "../auth/session";

export interface ObjectAclEntry {
  principalType: "ROLE" | "USER";
  principalId: string;
  permission: "READ" | "WRITE" | "INVOKE";
}

export async function fetchObjectAcl(objectPath: string): Promise<ObjectAclEntry[]> {
  const response = await fetch(
    `/api/v1/objects/by-path/acl?path=${encodeURIComponent(objectPath)}`,
    { headers: getAuthHeaders() }
  );
  if (!response.ok) {
    throw new Error(await response.text());
  }
  const data = (await response.json()) as { entries: ObjectAclEntry[] };
  return data.entries ?? [];
}

export async function saveObjectAcl(objectPath: string, entries: ObjectAclEntry[]): Promise<void> {
  const response = await fetch(
    `/api/v1/objects/by-path/acl?path=${encodeURIComponent(objectPath)}`,
    {
      method: "PUT",
      headers: {
        "Content-Type": "application/json",
        ...getAuthHeaders(),
      },
      body: JSON.stringify({ entries }),
    }
  );
  if (!response.ok) {
    throw new Error(await response.text());
  }
}
