const SCHEME_PATTERN = /^([a-z][a-z0-9+.-]*):/i;
const STRIPPED_CHARS = /[\u0000-\u0020]/g;

/**
 * True when navigating to `url` cannot execute script: only http(s) and relative
 * paths (/, ./, ../) are allowed; javascript:/data:/vbscript: are rejected.
 * Used for links coming from LLM agent responses (defense in depth at navigation).
 */
export function isSafeNavigationUrl(url: string | null | undefined): boolean {
  if (!url) {
    return false;
  }
  // Browsers strip ASCII whitespace/control chars while parsing URLs ("java\tscript:" still runs).
  const value = url.replace(STRIPPED_CHARS, "");
  if (!value) {
    return false;
  }
  if (value.startsWith("/") || value.startsWith("./") || value.startsWith("../")) {
    return true;
  }
  const scheme = value.match(SCHEME_PATTERN);
  if (!scheme) {
    // Scheme-less relative reference (e.g. "dashboards/main") — cannot be a script scheme.
    return true;
  }
  const name = scheme[1].toLowerCase();
  return name === "http" || name === "https";
}
