import { describe, expect, it, vi } from "vitest";
import userEvent from "@testing-library/user-event";
import { screen } from "@testing-library/react";
import PlatformBindingComposer from "./PlatformBindingComposer";
import { renderWithInspector } from "../test/renderWithInspector";
import { PLATFORM_BINDING_ENTRIES } from "../utils/platformBindings";

describe("PlatformBindingComposer", () => {
  it("inserts a built movingAvg expression", async () => {
    const user = userEvent.setup();
    const onInsert = vi.fn();
    const entry = PLATFORM_BINDING_ENTRIES.find((item) => item.id === "movingAvg");
    expect(entry).toBeDefined();

    renderWithInspector(
      <PlatformBindingComposer
        entry={entry!}
        context={{ variableNames: ["temperature"], objectPath: "root.platform.devices.lab" }}
        onInsert={onInsert}
        onClose={() => undefined}
      />,
    );

    await user.click(screen.getByRole("button", { name: "Insert expression" }));

    expect(onInsert).toHaveBeenCalledWith("movingAvg(temperature, 60)");
  });
});
