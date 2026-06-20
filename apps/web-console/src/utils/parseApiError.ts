/** Extracts a human-readable message from Spring ProblemDetail or plain text. */
export function parseApiError(text: string, fallback: string): string {
  const trimmed = text.trim();
  if (!trimmed) {
    return fallback;
  }
  try {
    const body = JSON.parse(trimmed) as { detail?: unknown; title?: unknown };
    if (typeof body.detail === "string" && body.detail.length > 0) {
      return body.detail;
    }
    if (typeof body.title === "string" && body.title.length > 0) {
      return body.title;
    }
  } catch {
    // plain text or HTML error page
  }
  return trimmed.length > 240 ? `${trimmed.slice(0, 240)}…` : trimmed;
}
