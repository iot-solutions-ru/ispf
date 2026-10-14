import { act, renderHook, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import {
  requestObjectTreeInvalidate,
  TREE_INVALIDATE_EVENT,
  useLazyObjectTree,
} from "./useLazyObjectTree";

const trackObjectPathSubscriptions = vi.fn(() => () => undefined);

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
      return [
        {
          id: "d1",
          path: "root.platform.devices.demo-1",
          type: "DEVICE",
          displayName: "Demo 1",
          description: "",
          revision: 1,
        },
      ];
    }
    return [];
  }),
}));

describe("useLazyObjectTree", () => {
  beforeEach(() => {
    trackObjectPathSubscriptions.mockClear();
  });

  it("subscribes bootstrap parents for WS structure events", async () => {
    renderHook(() => useLazyObjectTree(true));
    await waitFor(() => {
      expect(trackObjectPathSubscriptions).toHaveBeenCalled();
    });
    const last = trackObjectPathSubscriptions.mock.calls.at(-1)?.[0] as string[];
    expect(last).toEqual(expect.arrayContaining(["root", "root.platform"]));
  });

  it("adds expanded folders to WS interest paths", async () => {
    const { result } = renderHook(() => useLazyObjectTree(true));
    await waitFor(() => {
      expect(result.current.objects.length).toBeGreaterThan(0);
    });
    await act(async () => {
      await result.current.loadChildren("root.platform.devices", true);
    });
    await waitFor(() => {
      const last = trackObjectPathSubscriptions.mock.calls.at(-1)?.[0] as string[];
      expect(last).toEqual(expect.arrayContaining(["root.platform.devices"]));
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
});
