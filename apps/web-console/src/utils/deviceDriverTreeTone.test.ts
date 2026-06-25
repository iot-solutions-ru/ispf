import { describe, expect, it } from "vitest";
import { deviceDriverTreeClass, deviceDriverTreeTone } from "./deviceDriverTreeTone";

describe("deviceDriverTreeTone", () => {
  it("returns null for non-device nodes", () => {
    expect(deviceDriverTreeTone("FOLDER" as never, "RUNNING", true)).toBeNull();
  });

  it("maps stopped and missing status to gray", () => {
    expect(deviceDriverTreeTone("DEVICE", null, null)).toBe("stopped");
    expect(deviceDriverTreeTone("DEVICE", "STOPPED", false)).toBe("stopped");
    expect(deviceDriverTreeClass("DEVICE", "STOPPED", false)).toBe("driver-stopped");
  });

  it("maps error to red", () => {
    expect(deviceDriverTreeTone("DEVICE", "ERROR", false)).toBe("error");
    expect(deviceDriverTreeClass("DEVICE", "ERROR", false)).toBe("driver-error");
  });

  it("maps running disconnected to warning", () => {
    expect(deviceDriverTreeTone("DEVICE", "RUNNING", false)).toBe("warning");
    expect(deviceDriverTreeClass("DEVICE", "RUNNING", false)).toBe("driver-warning");
  });

  it("maps running connected to normal", () => {
    expect(deviceDriverTreeTone("DEVICE", "RUNNING", true)).toBe("normal");
    expect(deviceDriverTreeClass("DEVICE", "RUNNING", true)).toBeNull();
  });
});
