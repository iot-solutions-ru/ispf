import type { AiProviderStatus } from "../../api/ai";

export interface AgentChatAttachment {
  id: string;
  file: File;
  name: string;
  mimeType: string;
  kind: "text" | "image";
  previewUrl?: string;
}

export interface AgentAttachmentTypeSpec {
  kind: string;
  mimeTypes?: string[];
  extensions?: string[];
}

export function providerAttachmentTypes(
  provider: AiProviderStatus | undefined
): AgentAttachmentTypeSpec[] {
  return provider?.supportedAttachmentTypes ?? [];
}

export function providerAcceptAttribute(provider: AiProviderStatus | undefined): string {
  const types = providerAttachmentTypes(provider);
  const parts: string[] = [];
  for (const type of types) {
    if (type.mimeTypes) {
      parts.push(...type.mimeTypes);
    }
    if (type.extensions) {
      parts.push(...type.extensions);
    }
  }
  return parts.length > 0 ? parts.join(",") : "";
}

export function providerAllowsImages(provider: AiProviderStatus | undefined): boolean {
  return provider?.capabilities?.vision === true;
}

export function providerAllowsTextAttachments(provider: AiProviderStatus | undefined): boolean {
  return provider?.capabilities?.textAttachments !== false;
}

export function classifyAttachmentFile(
  file: File,
  provider: AiProviderStatus | undefined
): "text" | "image" | "unsupported" {
  const mime = (file.type || "").toLowerCase();
  const name = file.name.toLowerCase();
  const types = providerAttachmentTypes(provider);

  for (const spec of types) {
    if (spec.kind === "image") {
      if (mime.startsWith("image/")) {
        return "image";
      }
      if (spec.extensions?.some((ext) => name.endsWith(ext))) {
        return "image";
      }
    }
    if (spec.kind === "text") {
      if (mime.startsWith("text/") || mime.includes("json") || mime.includes("xml") || mime.includes("yaml")) {
        return "text";
      }
      if (spec.extensions?.some((ext) => name.endsWith(ext))) {
        return "text";
      }
    }
  }

  if (mime.startsWith("image/")) {
    return providerAllowsImages(provider) ? "image" : "unsupported";
  }
  return "unsupported";
}

export async function fileToBase64Payload(file: File): Promise<string> {
  const buffer = await file.arrayBuffer();
  const bytes = new Uint8Array(buffer);
  let binary = "";
  const chunkSize = 0x8000;
  for (let i = 0; i < bytes.length; i += chunkSize) {
    binary += String.fromCharCode(...bytes.subarray(i, i + chunkSize));
  }
  return btoa(binary);
}

export function buildAttachmentApiPayload(attachments: AgentChatAttachment[]) {
  return Promise.all(
    attachments.map(async (item) => ({
      name: item.name,
      mimeType: item.mimeType || item.file.type || "application/octet-stream",
      contentBase64: await fileToBase64Payload(item.file),
    }))
  );
}

export function revokeAttachmentPreviews(attachments: AgentChatAttachment[]) {
  for (const item of attachments) {
    if (item.previewUrl) {
      URL.revokeObjectURL(item.previewUrl);
    }
  }
}

export function createAttachmentFromFile(
  file: File,
  provider: AiProviderStatus | undefined
): AgentChatAttachment | "unsupported" | "vision-not-supported" {
  const kind = classifyAttachmentFile(file, provider);
  if (kind === "unsupported") {
    if ((file.type || "").startsWith("image/") || /\.(png|jpe?g|webp|gif)$/i.test(file.name)) {
      return "vision-not-supported";
    }
    return "unsupported";
  }
  const previewUrl = kind === "image" ? URL.createObjectURL(file) : undefined;
  return {
    id: `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
    file,
    name: file.name,
    mimeType: file.type || "application/octet-stream",
    kind,
    previewUrl,
  };
}

export function addAttachmentsFromFiles(
  current: AgentChatAttachment[],
  files: FileList | File[],
  provider: AiProviderStatus | undefined,
  onReject?: (reason: "unsupported" | "vision-not-supported") => void
): AgentChatAttachment[] {
  const next = [...current];
  for (const file of Array.from(files)) {
    const created = createAttachmentFromFile(file, provider);
    if (created === "unsupported" || created === "vision-not-supported") {
      onReject?.(created);
      continue;
    }
    if (!next.some((item) => item.name === created.name && item.file.size === created.file.size)) {
      next.push(created);
    }
  }
  return next;
}

function extensionForImageMime(mimeType: string): string {
  const mime = (mimeType || "").toLowerCase();
  if (mime.includes("jpeg") || mime.includes("jpg")) {
    return "jpg";
  }
  if (mime.includes("webp")) {
    return "webp";
  }
  if (mime.includes("gif")) {
    return "gif";
  }
  return "png";
}

/** Give clipboard screenshots a stable filename (browsers often use "image.png"). */
export function normalizeClipboardImageFile(file: File): File {
  const mime = file.type || "image/png";
  const ext = extensionForImageMime(mime);
  const genericName = !file.name || /^(image|blob|untitled)(\.\w+)?$/i.test(file.name);
  if (!genericName && file.type) {
    return file;
  }
  return new File([file], `clipboard-${Date.now()}.${ext}`, {
    type: mime,
    lastModified: file.lastModified || Date.now(),
  });
}

/**
 * Extract image files from a paste/drop DataTransfer (Ctrl+V screenshot, etc.).
 * Prefers clipboard items; falls back to files list.
 */
export function filesFromClipboardData(data: DataTransfer | null | undefined): File[] {
  if (!data) {
    return [];
  }
  const fromItems: File[] = [];
  if (data.items && data.items.length > 0) {
    for (const item of Array.from(data.items)) {
      if (item.kind !== "file") {
        continue;
      }
      const type = (item.type || "").toLowerCase();
      if (type && !type.startsWith("image/")) {
        continue;
      }
      const file = item.getAsFile();
      if (!file) {
        continue;
      }
      const fileType = (file.type || type || "").toLowerCase();
      const looksImage =
        fileType.startsWith("image/") || /\.(png|jpe?g|webp|gif)$/i.test(file.name || "");
      if (looksImage) {
        fromItems.push(normalizeClipboardImageFile(file));
      }
    }
  }
  if (fromItems.length > 0) {
    return fromItems;
  }
  return Array.from(data.files ?? [])
    .filter((file) => (file.type || "").startsWith("image/") || /\.(png|jpe?g|webp|gif)$/i.test(file.name))
    .map(normalizeClipboardImageFile);
}
