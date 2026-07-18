import { describe, expect, it, vi, afterEach } from "vitest";
import userEvent from "@testing-library/user-event";
import { cleanup, fireEvent, screen, within } from "@testing-library/react";
import BindingActivatorsEditor from "./BindingActivatorsEditor";
import { renderWithInspector } from "../../test/renderWithInspector";
import type { BindingActivators } from "../../types";

const baseActivators: BindingActivators = {
  onStartup: false,
  onVariableChange: [],
  onEvent: null,
  periodicMs: 0,
};

describe("BindingActivatorsEditor", () => {
  afterEach(() => {
    cleanup();
  });

  it("toggles onStartup", async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    renderWithInspector(
      <BindingActivatorsEditor activators={baseActivators} eventNames={[]} onChange={onChange} />,
    );

    await user.click(screen.getByRole("checkbox", { name: /Run on startup/i }));

    expect(onChange).toHaveBeenCalledWith(
      expect.objectContaining({
        onStartup: true,
      }),
    );
  });

  it("updates periodic interval", () => {
    const onChange = vi.fn();
    renderWithInspector(
      <BindingActivatorsEditor activators={baseActivators} eventNames={[]} onChange={onChange} />,
    );

    const fieldset = screen.getByRole("group", { name: "Activators" });
    const interval = within(fieldset).getByRole("spinbutton");
    fireEvent.change(interval, { target: { value: "5000" } });

    expect(onChange).toHaveBeenLastCalledWith(
      expect.objectContaining({
        periodicMs: 5000,
      }),
    );
  });
});
