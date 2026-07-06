import { describe, expect, it } from "vitest";
import { isWidgetMediaDataUrl, resolveWidgetMediaSrc } from "./widgetMediaUrl";

describe("widgetMediaUrl", () => {
  it("resolveWidgetMediaSrc keeps data and absolute URLs", () => {
    expect(resolveWidgetMediaSrc("data:image/png;base64,abc")).toBe("data:image/png;base64,abc");
    expect(resolveWidgetMediaSrc("https://cdn.example/x.png")).toBe("https://cdn.example/x.png");
  });

  it("resolveWidgetMediaSrc normalizes platform-relative paths", () => {
    expect(resolveWidgetMediaSrc("/lab-assets/fan.svg")).toBe("/lab-assets/fan.svg");
    expect(resolveWidgetMediaSrc("lab-assets/fan.svg")).toBe("/lab-assets/fan.svg");
  });

  it("isWidgetMediaDataUrl detects embedded media", () => {
    expect(isWidgetMediaDataUrl("data:image/png;base64,x")).toBe(true);
    expect(isWidgetMediaDataUrl("/lab-assets/x.png")).toBe(false);
  });
});
