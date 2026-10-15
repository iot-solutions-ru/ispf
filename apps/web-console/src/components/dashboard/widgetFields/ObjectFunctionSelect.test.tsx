import { cleanup, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { fetchObjectEditor } from "../../../api";
import { renderWithDashboard } from "../../../test/renderWithDashboard";
import { ObjectFunctionSelect } from "./widgetFieldPrimitives";

vi.mock("../../../api", async (importOriginal) => {
  const actual = await importOriginal<typeof import("../../../api")>();
  return {
    ...actual,
    fetchObjectEditor: vi.fn(),
  };
});

describe("ObjectFunctionSelect", () => {
  afterEach(cleanup);

  beforeEach(() => {
    vi.mocked(fetchObjectEditor).mockReset();
  });

  it("asks to choose an object and does not fetch when the path is empty", () => {
    renderWithDashboard(
      <ObjectFunctionSelect label="functionName" value="" onChange={() => {}} />,
    );
    expect(screen.getByRole("combobox", { name: "functionName" })).toBeDisabled();
    expect(
      screen.getByText("Choose an object first — the list is that object's functions."),
    ).toBeInTheDocument();
    expect(fetchObjectEditor).not.toHaveBeenCalled();
  });

  it("offers only the selected object's functions and keeps a saved name that is not in the list", async () => {
    vi.mocked(fetchObjectEditor).mockResolvedValue({
      functions: [{ name: "stop" }, { name: "start" }],
    } as Awaited<ReturnType<typeof fetchObjectEditor>>);
    const onChange = vi.fn();
    renderWithDashboard(
      <ObjectFunctionSelect
        label="functionName"
        objectPath="root.devices.pump"
        value="legacyFn"
        onChange={onChange}
      />,
    );

    const select = await screen.findByRole("combobox", { name: "functionName" });
    await waitFor(() => {
      expect(screen.getByRole("option", { name: "start" })).toBeInTheDocument();
    });
    expect(screen.getByRole("option", { name: "stop" })).toBeInTheDocument();
    expect(screen.getByRole("option", { name: "legacyFn" })).toBeInTheDocument();
    expect(screen.queryByRole("textbox")).not.toBeInTheDocument();

    await userEvent.selectOptions(select, "start");
    expect(onChange).toHaveBeenCalledWith("start");
    expect(fetchObjectEditor).toHaveBeenCalledWith("root.devices.pump");
  });
});
