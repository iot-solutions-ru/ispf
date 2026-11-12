import { afterEach, describe, expect, it } from "vitest";
import { cleanup, screen } from "@testing-library/react";
import { renderWithInspector } from "../test/renderWithInspector";
import DriverHonestyBadges from "./DriverHonestyBadges";
import type { DriverMetadata } from "../types/drivers";

function driver(patch: Partial<DriverMetadata>): DriverMetadata {
  return {
    id: "sample",
    name: "Sample",
    version: "1",
    description: "",
    vendor: "ISPF",
    configurationSchema: {},
    maturity: "PRODUCTION",
    ...patch,
  };
}

describe("DriverHonestyBadges", () => {
  afterEach(() => {
    cleanup();
  });

  it("shows loopback and read-only when write is not declared", () => {
    renderWithInspector(
      <DriverHonestyBadges
        driver={driver({
          evidence: "LOOPBACK_VERIFIED",
          capabilities: ["read"],
        })}
      />,
    );
    expect(screen.getByText("Loopback")).toBeInTheDocument();
    expect(screen.getByText("Read-only")).toBeInTheDocument();
    expect(screen.getByTitle("This driver does not declare write.")).toBeInTheDocument();
  });

  it("uses the server readOnly flag and the field gate", () => {
    renderWithInspector(
      <DriverHonestyBadges
        driver={driver({
          evidence: "FIELD",
          readOnly: false,
          capabilities: ["read"],
        })}
      />,
    );
    expect(screen.getByText("Field")).toBeInTheDocument();
    expect(screen.queryByText("Read-only")).not.toBeInTheDocument();
  });

  it("does not mark an older payload read-only when capabilities are missing", () => {
    renderWithInspector(<DriverHonestyBadges driver={driver({})} />);
    expect(screen.queryByText("Read-only")).not.toBeInTheDocument();
    expect(screen.queryByText("Loopback")).not.toBeInTheDocument();
  });
});
