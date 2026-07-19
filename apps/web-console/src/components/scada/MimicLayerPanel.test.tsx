/** @vitest-environment jsdom */
import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import MimicLayerPanel from "./MimicLayerPanel";
import type { MimicLayer } from "../../types/scadaMimic";

vi.mock("react-i18next", () => ({
  useTranslation: () => ({
    t: (key: string, opts?: { n?: number }) =>
      opts?.n != null ? `${key}:${opts.n}` : key,
  }),
}));

describe("MimicLayerPanel (BL-147)", () => {
  it("toggles layer visibility and adds layers", async () => {
    const user = userEvent.setup();
    const layers: MimicLayer[] = [
      { id: "layer-default", name: "Main", visible: true },
      { id: "layer-2", name: "Overlay", visible: true },
    ];
    const onUpdateLayers = vi.fn();
    const onActiveLayerChange = vi.fn();
    render(
      <MimicLayerPanel
        layers={layers}
        activeLayerId="layer-default"
        onActiveLayerChange={onActiveLayerChange}
        onUpdateLayers={onUpdateLayers}
      />
    );

    const visibility = screen.getAllByRole("checkbox");
    await user.click(visibility[0]);
    expect(onUpdateLayers).toHaveBeenCalledWith([
      { id: "layer-default", name: "Main", visible: false },
      { id: "layer-2", name: "Overlay", visible: true },
    ]);

    await user.click(screen.getByRole("button", { name: "layers.add" }));
    expect(onUpdateLayers).toHaveBeenCalled();
    expect(onActiveLayerChange).toHaveBeenCalled();
  });
});
