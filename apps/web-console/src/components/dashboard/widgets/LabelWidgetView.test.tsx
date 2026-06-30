import { describe, expect, it, afterEach } from "vitest";
import { cleanup, screen } from "@testing-library/react";
import LabelWidgetView from "./LabelWidgetView";
import { newWidget } from "../../../types/dashboard";
import { renderWithDashboard } from "../../../test/renderWithDashboard";

describe("LabelWidgetView", () => {
  afterEach(() => {
    cleanup();
  });

  it("renders static label text", () => {
    const widget = {
      ...newWidget("label", 0),
      title: "Section",
      text: "Tank level overview",
    };

    renderWithDashboard(<LabelWidgetView widget={widget} />);

    expect(screen.getByText("Tank level overview")).toBeInTheDocument();
    expect(screen.getByText("Section")).toBeInTheDocument();
  });

  it("prefers dashboard param value over static text", () => {
    const widget = {
      ...newWidget("label", 0),
      title: "Dynamic",
      text: "Fallback",
      paramKey: "headline",
    };

    renderWithDashboard(<LabelWidgetView widget={widget} />, {
      session: { params: { headline: "Live headline" } },
    });

    expect(screen.getByText("Live headline")).toBeInTheDocument();
    expect(screen.queryByText("Fallback")).not.toBeInTheDocument();
  });
});
