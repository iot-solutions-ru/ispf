import { act, renderHook, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import {
  requestObjectTreeInvalidate,
  TREE_INVALIDATE_EVENT,
  useLazyObjectTree,
} from "./useLazyObjectTree";

const trackObjectPathSubscriptions = vi.fn(() => () => undefined);
const treeMock = vi.hoisted(() => ({ deviceCount: 1 }));

vi.mock("./useObjectWebSocket", async () => {
  const actual = await vi.importActual<typeof import("./useObjectWebSocket")>("./useObjectWebSocket");
  return {
    ...actual,
    useObjectPathsSubscription: (paths: string[]) => {
      trackObjectPathSubscriptions(paths);
    },
  };
});

vi.mock("../api", () => ({
  fetchObjects: vi.fn(async (parentPath: string) => {
    if (parentPath === "root") {
      return [
        {
          id: "platform",
          path: "root.platform",
          type: "PLATFORM",
          displayName: "Platform",
          description: "",
          revision: 1,
        },
      ];
    }
    if (parentPath === "root.platform") {
      return [
        {
          id: "devices",
          path: "root.platform.devices",
          type: "DEVICES",
          displayName: "Devices",
          description: "",
          revision: 1,
        },
      ];
    }
    if (parentPath === "root.platform.devices") {
      return Array.from({ length: treeMock.deviceCount }, (_, index) => ({
        id: `d${index}`,
        path: `root.platform.devices.demo-${index}`,
        type: "DEVICE",
        displayName: `Demo ${index}`,
        description: "",
        revision: 1,
      }));
    }
    return [];
  }),
}));

describe("useLazyObjectTree", () => {
  beforeEach(() => {
    trackObjectPathSubscriptions.mockClear();
    treeMock.deviceCount = 1;
  });

  it("does not path-wide subscribe bootstrap roots (avoids platform telemetry flood)", async () => {
    renderHook(() => useLazyObjectTree(true));
    await waitFor(() => {
      expect(trackObjectPathSubscriptions).toHaveBeenCalled();
    });
    const last = (trackObjectPathSubscriptions.mock.calls.at(-1)?.[0] as string[]) ?? [];
    expect(last).not.toContain("root");
    expect(last).not.toContain("root.platform");
  });

  it("adds expanded non-bootstrap folders to WS interest paths", async () => {
    const { result } = renderHook(() => useLazyObjectTree(true));
    await waitFor(() => {
      expect(result.current.objects.length).toBeGreaterThan(0);
    });
    await act(async () => {
      await result.current.loadChildren("root.platform.devices", true);
    });
    await waitFor(() => {
      const last = (trackObjectPathSubscriptions.mock.calls.at(-1)?.[0] as string[]) ?? [];
      expect(last).toContain("root.platform.devices");
      expect(last).not.toContain("root.platform");
    });
  });

  it("requestObjectTreeInvalidate triggers a full reload", async () => {
    const { result } = renderHook(() => useLazyObjectTree(true));
    await waitFor(() => expect(result.current.objects.length).toBeGreaterThan(0));
    const before = result.current.objects.length;
    await act(async () => {
      requestObjectTreeInvalidate();
    });
    await waitFor(() => {
      expect(result.current.objects.length).toBe(before);
    });
    expect(TREE_INVALIDATE_EVENT).toBe("ispf-tree-invalidate");
  });

  it("records folders larger than the live-update cap and does not subscribe them", async () => {
    treeMock.deviceCount = 65;
    const { result } = renderHook(() => useLazyObjectTree(true));
    await waitFor(() => {
      expect(result.current.objects.length).toBeGreaterThan(0);
    });
    await act(async () => {
      await result.current.loadChildren("root.platform.devices", true);
    });
    await waitFor(() => {
      expect(result.current.wsCappedPaths).toContain("root.platform.devices");
    });
    const last = (trackObjectPathSubscriptions.mock.calls.at(-1)?.[0] as string[]) ?? [];
    expect(last).not.toContain("root.platform.devices");
  });

  it("force-reloads capped folders on re-expand (live updates are paused)", async () => {
    const { fetchObjects } = await import("../api");
    treeMock.deviceCount = 65;
    const { result } = renderHook(() => useLazyObjectTree(true));
    await waitFor(() => {
      expect(result.current.objects.length).toBeGreaterThan(0);
    });
    await act(async () => {
      await result.current.loadChildren("root.platform.devices", true);
    });
    await waitFor(() => {
      expect(result.current.wsCappedPaths).toContain("root.platform.devices");
    });
    const callsBefore = vi.mocked(fetchObjects).mock.calls.filter(
      ([parent]) => parent === "root.platform.devices",
    ).length;
    await act(async () => {
      // Mirrors App handleTreeLoadChildren(path, forceRefresh=true) for capped folders.
      const force = true && result.current.wsCappedPaths.includes("root.platform.devices");
      await result.current.loadChildren("root.platform.devices", force);
    });
    const callsAfter = vi.mocked(fetchObjects).mock.calls.filter(
      ([parent]) => parent === "root.platform.devices",
    ).length;
    expect(callsAfter).toBe(callsBefore + 1);
  });

  it("prunes deleted structural children when a capped folder is force-reloaded", async () => {
    treeMock.deviceCount = 65;
    const { result } = renderHook(() => useLazyObjectTree(true));
    await waitFor(() => {
      expect(result.current.objects.length).toBeGreaterThan(0);
    });
    await act(async () => {
      await result.current.loadChildren("root.platform.devices", true);
    });
    await waitFor(() => {
      expect(result.current.objects.some((obj) => obj.path.endsWith(".demo-64"))).toBe(true);
      expect(result.current.wsCappedPaths).toContain("root.platform.devices");
    });
    treeMock.deviceCount = 60;
    await act(async () => {
      await result.current.loadChildren("root.platform.devices", true);
    });
    await waitFor(() => {
      expect(result.current.objects.some((obj) => obj.path.endsWith(".demo-64"))).toBe(false);
      expect(result.current.objects.some((obj) => obj.path.endsWith(".demo-59"))).toBe(true);
      expect(result.current.wsCappedPaths).not.toContain("root.platform.devices");
    });
  });
});
