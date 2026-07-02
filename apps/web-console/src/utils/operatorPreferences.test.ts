import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  isOperatorAlarmSoundEnabled,
  isOperatorBrowserNotifyEnabled,
  setOperatorAlarmSoundEnabled,
  setOperatorBrowserNotifyEnabled,
  showOperatorAlarmNotification,
} from "./operatorPreferences";

function mockBrowserStorage() {
  const store = new Map<string, string>();
  vi.stubGlobal("localStorage", {
    getItem: (key: string) => (store.has(key) ? store.get(key)! : null),
    setItem: (key: string, value: string) => {
      store.set(key, value);
    },
    removeItem: (key: string) => {
      store.delete(key);
    },
    clear: () => {
      store.clear();
    },
  });
  vi.stubGlobal("window", {
    dispatchEvent: vi.fn(),
  });
}

describe("operatorPreferences", () => {
  beforeEach(() => {
    mockBrowserStorage();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("defaults alarm sound to off", () => {
    expect(isOperatorAlarmSoundEnabled()).toBe(false);
  });

  it("persists alarm sound preference", () => {
    setOperatorAlarmSoundEnabled(true);
    expect(isOperatorAlarmSoundEnabled()).toBe(true);
    setOperatorAlarmSoundEnabled(false);
    expect(isOperatorAlarmSoundEnabled()).toBe(false);
  });

  it("defaults browser notifications to off", () => {
    expect(isOperatorBrowserNotifyEnabled()).toBe(false);
  });

  it("persists browser notification preference", () => {
    setOperatorBrowserNotifyEnabled(true);
    expect(isOperatorBrowserNotifyEnabled()).toBe(true);
  });

  it("skips browser notification when disabled", () => {
    const notify = vi.fn();
    vi.stubGlobal("Notification", notify);
    showOperatorAlarmNotification("High temp", "88 C", "alarm-1");
    expect(notify).not.toHaveBeenCalled();
  });
});
