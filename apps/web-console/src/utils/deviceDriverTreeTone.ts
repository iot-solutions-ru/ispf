import type { ObjectType } from "../types";

export type DeviceDriverTreeTone = "normal" | "stopped" | "warning" | "error";

/** CSS class suffix for explorer tree rows (`driver-stopped`, …). */
export function deviceDriverTreeClass(
  type: ObjectType,
  driverStatus?: string | null,
  driverConnected?: boolean | null,
): string | null {
  const tone = deviceDriverTreeTone(type, driverStatus, driverConnected);
  if (!tone || tone === "normal") {
    return null;
  }
  return `driver-${tone}`;
}

export function deviceDriverTreeTone(
  type: ObjectType,
  driverStatus?: string | null,
  driverConnected?: boolean | null,
): DeviceDriverTreeTone | null {
  if (type !== "DEVICE") {
    return null;
  }
  if (!driverStatus || driverStatus === "STOPPED") {
    return "stopped";
  }
  if (driverStatus === "ERROR") {
    return "error";
  }
  if (driverStatus === "RUNNING" && driverConnected === false) {
    return "warning";
  }
  return "normal";
}
