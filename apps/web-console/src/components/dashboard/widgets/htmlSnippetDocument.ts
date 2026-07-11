/** Decode common HTML entities when snippet was stored escaped. */
export function decodeHtmlSnippetEntities(html: string): string {
  if (!/&lt;|&gt;|&amp;|&#\d+;|&#x/i.test(html)) {
    return html;
  }
  return html
    .replace(/&lt;/gi, "<")
    .replace(/&gt;/gi, ">")
    .replace(/&quot;/gi, '"')
    .replace(/&#39;/gi, "'")
    .replace(/&amp;/gi, "&");
}

const BLOCKED_HTML_TAGS = /<\/?(script|iframe|object|embed|link|meta|style|foreignObject|base|form)\b[^>]*>/gi;
const EVENT_ATTRS = /\s(on[a-z]+|formaction|xlink:href|href)\s*=\s*("[^"]*"|'[^']*'|[^\s>]+)/gi;

/** Strip executable markup from dashboard HTML fragments before innerHTML. */
export function sanitizeHtmlSnippet(html: string): string {
  let sanitized = normalizeHtmlSnippet(html);
  if (!sanitized) {
    return "";
  }
  sanitized = sanitized.replace(BLOCKED_HTML_TAGS, "");
  sanitized = sanitized.replace(EVENT_ATTRS, (match, attr: string, val: string) => {
    const lower = attr.toLowerCase();
    if (lower.startsWith("on")) {
      return "";
    }
    const value = val.replace(/^['"]|['"]$/g, "").trim().toLowerCase();
    if (lower === "href" || lower === "xlink:href") {
      if (value.startsWith("javascript:") || value.startsWith("data:text/html")) {
        return "";
      }
    }
    return match;
  });
  return sanitized;
}

export function normalizeHtmlSnippet(html: string): string {
  return decodeHtmlSnippetEntities(html).trim();
}

export function isFullHtmlDocument(html: string): boolean {
  const trimmed = normalizeHtmlSnippet(html);
  return /^\s*<!DOCTYPE/i.test(trimmed) || /^\s*<html[\s>]/i.test(trimmed);
}

/** True when snippet embeds executable JavaScript. */
export function htmlSnippetContainsScript(html: string): boolean {
  return /<script[\s>]/i.test(normalizeHtmlSnippet(html));
}

/** Full pages and JS snippets must run in an isolated iframe — innerHTML cannot host documents. */
export function htmlSnippetRequiresIframe(html: string): boolean {
  const normalized = normalizeHtmlSnippet(html);
  if (!normalized) return false;
  return isFullHtmlDocument(normalized) || htmlSnippetContainsScript(normalized);
}

const IFRAME_ONLY_SNIPPET =
  /^<iframe\b([^>]*)>(?:\s*<\/iframe>)?\s*$/i;

function readHtmlAttribute(attrs: string, name: string): string | undefined {
  const re = new RegExp(`\\b${name}\\s*=\\s*(['"])(.*?)\\1`, "i");
  const match = attrs.match(re);
  return match?.[2]?.trim();
}

/** Snippet that is only a single external iframe — rendered with platform-controlled `src`. */
export function parseHtmlSnippetIframeEmbed(html: string): { src: string; title?: string } | null {
  const normalized = normalizeHtmlSnippet(html).replace(/<!--[\s\S]*?-->/g, "").trim();
  if (!normalized) return null;

  const tagMatch = normalized.match(IFRAME_ONLY_SNIPPET);
  if (!tagMatch) return null;

  const src = readHtmlAttribute(tagMatch[1], "src");
  if (!src || !/^https?:\/\//i.test(src)) return null;

  const title = readHtmlAttribute(tagMatch[1], "title");
  return title ? { src, title } : { src };
}

/** Wrap HTML fragment in a minimal document for iframe srcdoc. */
export function buildHtmlSnippetSrcDoc(html: string): string {
  const trimmed = normalizeHtmlSnippet(html);
  if (isFullHtmlDocument(trimmed)) {
    return trimmed;
  }
  return `<!DOCTYPE html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><style>html,body{margin:0;padding:0;width:100%;height:100%;box-sizing:border-box}*,*::before,*::after{box-sizing:inherit}</style></head><body>${trimmed}</body></html>`;
}

/** For HTML fragments with script (not a full document). */
export function mountHtmlWithScripts(container: HTMLElement, html: string): void {
  const normalized = normalizeHtmlSnippet(html);
  container.innerHTML = normalized;
  container.querySelectorAll("script").forEach((oldScript) => {
    const script = document.createElement("script");
    for (const attr of oldScript.attributes) {
      script.setAttribute(attr.name, attr.value);
    }
    script.text = oldScript.text;
    oldScript.replaceWith(script);
  });
}
