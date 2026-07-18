export const OPERATOR_ALARM_SOUND_KEY = "ispf-operator-alarm-sound";
export const OPERATOR_BROWSER_NOTIFY_KEY = "ispf-operator-browser-notify";

export const OPERATOR_PREFERENCES_CHANGED_EVENT = "ispf-operator-preferences-changed";

export function isOperatorAlarmSoundEnabled(): boolean {
  try {
    return localStorage.getItem(OPERATOR_ALARM_SOUND_KEY) === "1";
  } catch {
    return false;
  }
}

export function setOperatorAlarmSoundEnabled(enabled: boolean): void {
  try {
    localStorage.setItem(OPERATOR_ALARM_SOUND_KEY, enabled ? "1" : "0");
    dispatchPreferencesChanged();
  } catch {
    // ignore private mode
  }
}

export function isOperatorBrowserNotifyEnabled(): boolean {
  try {
    return localStorage.getItem(OPERATOR_BROWSER_NOTIFY_KEY) === "1";
  } catch {
    return false;
  }
}

export function setOperatorBrowserNotifyEnabled(enabled: boolean): void {
  try {
    localStorage.setItem(OPERATOR_BROWSER_NOTIFY_KEY, enabled ? "1" : "0");
    dispatchPreferencesChanged();
  } catch {
    // ignore private mode
  }
}

export function dispatchPreferencesChanged(): void {
  window.dispatchEvent(new CustomEvent(OPERATOR_PREFERENCES_CHANGED_EVENT));
}

export async function requestBrowserNotificationPermission(): Promise<NotificationPermission> {
  if (typeof Notification === "undefined") {
    return "denied";
  }
  if (Notification.permission === "granted" || Notification.permission === "denied") {
    return Notification.permission;
  }
  return Notification.requestPermission();
}

export function showOperatorAlarmNotification(title: string, body: string, tag: string): void {
  if (typeof Notification === "undefined") {
    return;
  }
  if (!isOperatorBrowserNotifyEnabled() || Notification.permission !== "granted") {
    return;
  }
  try {
    new Notification(title, { body, tag, silent: true });
  } catch {
    // ignore unsupported / blocked notifications
  }
}
