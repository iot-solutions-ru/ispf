import { describe, expect, it } from "vitest";
import {
  addAttachmentsFromFiles,
  filesFromClipboardData,
  normalizeClipboardImageFile,
  type AgentChatAttachment,
} from "./agentChatAttachments";
import type { AiProviderStatus } from "../../api/ai";

const visionProvider: AiProviderStatus = {
  enabled: true,
  providerId: "openai-compatible",
  available: true,
  model: "qwen36",
  capabilities: { vision: true, textAttachments: true },
  supportedAttachmentTypes: [
    {
      kind: "image",
      mimeTypes: ["image/png", "image/jpeg", "image/webp", "image/gif"],
      extensions: [".png", ".jpg", ".jpeg", ".webp", ".gif"],
    },
    {
      kind: "text",
      mimeTypes: ["text/plain", "application/json"],
      extensions: [".txt", ".json"],
    },
  ],
};

function mockClipboardData(files: File[]): DataTransfer {
  const items = files.map((file) => ({
    kind: "file" as const,
    type: file.type,
    getAsFile: () => file,
  }));
  return {
    items: items as unknown as DataTransferItemList,
    files: files as unknown as FileList,
    types: files.map((f) => f.type),
    getData: () => "",
    setData: () => undefined,
    clearData: () => undefined,
    setDragImage: () => undefined,
    dropEffect: "none",
    effectAllowed: "all",
  } as DataTransfer;
}

describe("agentChatAttachments clipboard", () => {
  it("normalizes generic clipboard image names", () => {
    const raw = new File([new Uint8Array([1, 2, 3])], "image.png", { type: "image/png" });
    const named = normalizeClipboardImageFile(raw);
    expect(named.name).toMatch(/^clipboard-\d+\.png$/);
    expect(named.type).toBe("image/png");
  });

  it("extracts images from clipboard DataTransfer", () => {
    const png = new File([new Uint8Array([9])], "image.png", { type: "image/png" });
    const files = filesFromClipboardData(mockClipboardData([png]));
    expect(files).toHaveLength(1);
    expect(files[0].type).toBe("image/png");
    expect(files[0].name).toMatch(/^clipboard-\d+\.png$/);
  });

  it("ignores non-image clipboard items", () => {
    const txt = new File(["hello"], "note.txt", { type: "text/plain" });
    expect(filesFromClipboardData(mockClipboardData([txt]))).toHaveLength(0);
  });

  it("adds pasted images when vision is enabled", () => {
    const png = new File([new Uint8Array([1])], "clipboard-1.png", { type: "image/png" });
    const next = addAttachmentsFromFiles([] as AgentChatAttachment[], [png], visionProvider);
    expect(next).toHaveLength(1);
    expect(next[0].kind).toBe("image");
  });

  it("rejects images when vision is disabled", () => {
    const noVision: AiProviderStatus = {
      ...visionProvider,
      capabilities: { vision: false, textAttachments: true },
      supportedAttachmentTypes: visionProvider.supportedAttachmentTypes!.filter((t) => t.kind !== "image"),
    };
    const reasons: string[] = [];
    const png = new File([new Uint8Array([1])], "clipboard-1.png", { type: "image/png" });
    const next = addAttachmentsFromFiles([], [png], noVision, (reason) => reasons.push(reason));
    expect(next).toHaveLength(0);
    expect(reasons).toEqual(["vision-not-supported"]);
  });
});
