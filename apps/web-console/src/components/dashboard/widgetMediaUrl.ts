/** Max size for inline dashboard media (stored as data URL in layout JSON). */
export const MAX_WIDGET_MEDIA_BYTES = 2 * 1024 * 1024;

export function resolveWidgetMediaSrc(url?: string): string {
  const trimmed = url?.trim();
  if (!trimmed) return "";
  if (
    trimmed.startsWith("data:") ||
    trimmed.startsWith("http://") ||
    trimmed.startsWith("https://") ||
    trimmed.startsWith("//")
  ) {
    return trimmed;
  }
  if (trimmed.startsWith("/")) return trimmed;
  return `/${trimmed.replace(/^\//, "")}`;
}

export function readFileAsDataUrl(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(String(reader.result ?? ""));
    reader.onerror = () => reject(reader.error ?? new Error("read failed"));
    reader.readAsDataURL(file);
  });
}

export function isWidgetMediaDataUrl(url?: string): boolean {
  return Boolean(url?.trim().startsWith("data:"));
}
