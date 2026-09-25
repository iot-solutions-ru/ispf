import { describe, expect, it, vi, beforeEach, afterEach } from "vitest";
import userEvent from "@testing-library/user-event";
import { cleanup, screen, waitFor } from "@testing-library/react";
import type { ObjectSummary, ObjectType } from "../types";
import { renderWithInspector } from "../test/renderWithInspector";
import ObjectPathField from "./ObjectPathField";
import * as api from "../api";

vi.mock("../api", async (importOriginal) => {
  const actual = await importOriginal<typeof import("../api")>();
  return {
    ...actual,
    fetchObjects: vi.fn(),
  };
});

function object(path: string, type: ObjectType, displayName: string): ObjectSummary {
  return {
    id: path,
    path,
    type,
    displayName,
    description: "",
    templateId: null,
    createdAt: "",
    sortOrder: 0,
    variableNames: [],
    eventNames: [],
  };
}

describe("ObjectPathField", () => {
  afterEach(() => {
    cleanup();
  });

  beforeEach(() => {
    vi.mocked(api.fetchObjects).mockImplementation(async (parent) => {
      if (parent === "root") {
        return [
          object("root.platform", "PLATFORM", "Platform"),
          object("root.VD1", "DEVICE", "VD1"),
        ];
      }
      return [];
    });
  });

  it("keeps a typed path", async () => {
    const onChange = vi.fn();
    const user = userEvent.setup();
    renderWithInspector(
      <ObjectPathField value="" onChange={onChange} placeholder="root.platform..." />,
    );

    await user.click(screen.getByPlaceholderText("root.platform..."));
    await user.paste("root.platform.devices.VD1");

    expect(onChange).toHaveBeenCalledWith("root.platform.devices.VD1");
  });

  it("rejects a catalog type outside filterTypes and accepts a device", async () => {
    const onChange = vi.fn();
    const user = userEvent.setup();
    renderWithInspector(
      <ObjectPathField value="" onChange={onChange} filterTypes={["DEVICE"]} />,
    );

    await user.click(screen.getByRole("button", { name: "Browse tree" }));
    expect(await screen.findByText("Selectable types: DEVICE")).toBeInTheDocument();

    const platform = await screen.findByTitle("root.platform");
    await user.click(platform);
    expect(onChange).not.toHaveBeenCalled();

    const device = await screen.findByTitle("root.VD1");
    await user.click(device);
    await waitFor(() => expect(onChange).toHaveBeenCalledWith("root.VD1"));
  });
});
