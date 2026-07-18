import { describe, expect, it } from "vitest";
import {
  buildGanttTicks,
  computeDataBounds,
  fitGanttViewport,
  formatGanttTick,
  ganttBarLayout,
  panGanttViewport,
  patchGanttRowTimes,
  timeAtTrackPixel,
  zoomGanttViewport,
} from "./ganttChartView";

describe("computeDataBounds", () => {
  it("returns unit span when there are no rows", () => {
    expect(computeDataBounds([])).toEqual({ min: 0, max: 1, span: 1 });
  });

  it("returns bounds across all rows", () => {
    expect(
      computeDataBounds([
        { id: 0, label: "A", start: 2, end: 5 },
        { id: 1, label: "B", start: 0, end: 8 },
      ])
    ).toEqual({ min: 0, max: 8, span: 8 });
  });
});

describe("fitGanttViewport", () => {
  it("adds padding around data range", () => {
    const viewport = fitGanttViewport(0, 10, 0.1);
    expect(viewport.start).toBeLessThan(0);
    expect(viewport.end).toBeGreaterThan(10);
  });
});

describe("zoomGanttViewport", () => {
  it("zooms in around anchor", () => {
    const base = fitGanttViewport(0, 10);
    const zoomed = zoomGanttViewport(base, 2, 5, 0, 10);
    expect(zoomed.end - zoomed.start).toBeLessThan(base.end - base.start);
    expect(zoomed.start).toBeLessThan(5);
    expect(zoomed.end).toBeGreaterThan(5);
  });
});

describe("panGanttViewport", () => {
  it("shifts the visible window", () => {
    const base = fitGanttViewport(0, 10);
    const panned = panGanttViewport(base, 2, 0, 10);
    expect(panned.start).toBeGreaterThan(base.start);
    expect(panned.end).toBeGreaterThan(base.end);
  });
});

describe("ganttBarLayout", () => {
  it("maps bar to viewport percentages", () => {
    const layout = ganttBarLayout({ start: 2, end: 6 }, { start: 0, end: 10 });
    expect(layout.visible).toBe(true);
    expect(layout.leftPct).toBeCloseTo(20);
    expect(layout.widthPct).toBeCloseTo(40);
  });

  it("hides bars outside viewport", () => {
    const layout = ganttBarLayout({ start: 20, end: 24 }, { start: 0, end: 10 });
    expect(layout.visible).toBe(false);
  });
});

describe("timeAtTrackPixel", () => {
  it("converts track position to time", () => {
    const time = timeAtTrackPixel(50, 0, 100, { start: 0, end: 10 });
    expect(time).toBeCloseTo(5);
  });
});

describe("buildGanttTicks", () => {
  it("returns evenly spaced ticks", () => {
    const ticks = buildGanttTicks({ start: 0, end: 10 }, 3);
    expect(ticks).toEqual([0, 5, 10]);
  });
});

describe("formatGanttTick", () => {
  it("formats small integers as plain numbers", () => {
    expect(formatGanttTick(42)).toBe("42");
  });

  it("returns empty string for non-finite values", () => {
    expect(formatGanttTick(Number.NaN)).toBe("");
  });
});

describe("patchGanttRowTimes", () => {
  it("updates start/end on a list row", () => {
    const record = {
      schema: { name: "tasks", fields: [] },
      rows: [
        { name: "A", start: 1, end: 3 },
        { name: "B", start: 4, end: 6 },
      ],
    };
    const next = patchGanttRowTimes(record, 1, "start", "end", 5, 9);
    expect(next.rows[1]).toMatchObject({ start: 5, end: 9 });
    expect(next.rows[0]).toMatchObject({ start: 1, end: 3 });
  });
});
