import { getAuthHeaders } from "../auth/session";
import { parseApiError } from "../utils/parseApiError";

export interface OperatorAppDocumentMeta {
  docId: string;
  filename: string;
  mimeType: string;
  description: string;
  byteSize: number;
  charCount: number;
  updatedAt: string;
}

export interface OperatorAppDocumentsList {
  appId: string;
  count: number;
  documents: OperatorAppDocumentMeta[];
}

export async function fetchOperatorAppDocuments(appId: string): Promise<OperatorAppDocumentsList> {
  const response = await fetch(`/api/v1/operator-apps/${encodeURIComponent(appId)}/documents`, {
    headers: getAuthHeaders(),
  });
  if (!response.ok) {
    throw new Error(parseApiError(await response.text(), `Documents list failed: ${response.status}`));
  }
  return response.json() as Promise<OperatorAppDocumentsList>;
}

export async function uploadOperatorAppDocument(
  appId: string,
  file: File,
  description?: string
): Promise<OperatorAppDocumentMeta> {
  const form = new FormData();
  form.append("file", file);
  if (description?.trim()) {
    form.append("description", description.trim());
  }
  const response = await fetch(`/api/v1/operator-apps/${encodeURIComponent(appId)}/documents`, {
    method: "POST",
    headers: getAuthHeaders(),
    body: form,
  });
  if (!response.ok) {
    throw new Error(parseApiError(await response.text(), `Upload failed: ${response.status}`));
  }
  return response.json() as Promise<OperatorAppDocumentMeta>;
}

export async function deleteOperatorAppDocument(appId: string, docId: string): Promise<void> {
  const response = await fetch(
    `/api/v1/operator-apps/${encodeURIComponent(appId)}/documents/${encodeURIComponent(docId)}`,
    {
      method: "DELETE",
      headers: getAuthHeaders(),
    }
  );
  if (!response.ok) {
    throw new Error(parseApiError(await response.text(), `Delete failed: ${response.status}`));
  }
}
