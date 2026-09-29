import { afterEach, describe, expect, it, vi } from "vitest";
import userEvent from "@testing-library/user-event";
import { cleanup, render, screen } from "@testing-library/react";
import i18n from "i18next";
import { I18nextProvider, initReactI18next } from "react-i18next";
import enInspector from "../../locales/en/inspector.json";
import type { BlueprintAttachmentDto } from "../../types/blueprints";
import BlueprintAttachmentsSection from "./BlueprintAttachmentsSection";
import { attachmentsForBlueprint } from "./blueprintAttachments";

const testI18n = i18n.createInstance();
void testI18n.use(initReactI18next).init({
  lng: "en",
  resources: { en: { inspector: enInspector } },
  interpolation: { escapeValue: false },
});

function attachment(
  partial: Partial<BlueprintAttachmentDto> & Pick<BlueprintAttachmentDto, "id" | "blueprintId" | "objectPath">
): BlueprintAttachmentDto {
  return {
    blueprintName: "demo-mixin",
    blueprintType: "MIXIN",
    attachedAt: "2026-09-29T10:00:00Z",
    ...partial,
  };
}

describe("attachmentsForBlueprint", () => {
  it("keeps only rows for the requested blueprint id", () => {
    const rows = [
      attachment({ id: "a1", blueprintId: "mixin-1", objectPath: "root.VD1" }),
      attachment({ id: "a2", blueprintId: "mixin-2", objectPath: "root.file" }),
      attachment({ id: "a3", blueprintId: "mixin-1", objectPath: "root.file" }),
    ];
    expect(attachmentsForBlueprint(rows, "mixin-1").map((row) => row.objectPath)).toEqual([
      "root.VD1",
      "root.file",
    ]);
  });
});

describe("BlueprintAttachmentsSection", () => {
  afterEach(() => {
    cleanup();
  });

  it("shows an empty state when the mixin is not applied", () => {
    render(
      <I18nextProvider i18n={testI18n}>
        <BlueprintAttachmentsSection attachments={[]} />
      </I18nextProvider>
    );
    expect(screen.getByText("Not applied anywhere")).toBeInTheDocument();
    expect(screen.getByText("Applied to")).toBeInTheDocument();
  });

  it("lists attachment paths and selects one in the tree", async () => {
    const onSelectPath = vi.fn();
    const user = userEvent.setup();
    render(
      <I18nextProvider i18n={testI18n}>
        <BlueprintAttachmentsSection
          attachments={[
            attachment({ id: "a1", blueprintId: "mixin-1", objectPath: "root.VD1" }),
            attachment({
              id: "a2",
              blueprintId: "mixin-1",
              objectPath: "root.file",
              warnings: [{ kind: "VARIABLE", name: "temp", previousBlueprintId: null, appliedBlueprintId: "mixin-1" }],
            }),
          ]}
          onSelectPath={onSelectPath}
        />
      </I18nextProvider>
    );

    expect(screen.getByText("root.VD1")).toBeInTheDocument();
    expect(screen.getByText("root.file")).toBeInTheDocument();
    expect(screen.getByText(/VARIABLE/)).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "root.VD1" }));
    expect(onSelectPath).toHaveBeenCalledWith("root.VD1");
  });
});
