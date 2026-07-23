/** @vitest-environment jsdom */
import { describe, expect, it } from "vitest";
import {
  buildHtmlSnippetSrcDoc,
  decodeHtmlSnippetEntities,
  htmlSnippetContainsScript,
  htmlSnippetRequiresIframe,
  isFullHtmlDocument,
  parseHtmlSnippetIframeEmbed,
  sanitizeHtmlSnippet,
} from "./htmlSnippetDocument";

describe("htmlSnippetDocument", () => {
  it("detects script tags", () => {
    expect(htmlSnippetContainsScript("<p>ok</p>")).toBe(false);
    expect(htmlSnippetContainsScript("<script>alert(1)</script>")).toBe(true);
  });

  it("detects full HTML documents", () => {
    expect(isFullHtmlDocument("<!DOCTYPE html><html><body>x</body></html>")).toBe(true);
    expect(isFullHtmlDocument("<p>fragment</p>")).toBe(false);
  });

  it("requires iframe for documents and scripts", () => {
    expect(htmlSnippetRequiresIframe("<p>ok</p>")).toBe(false);
    expect(htmlSnippetRequiresIframe("<script>1</script>")).toBe(true);
    expect(htmlSnippetRequiresIframe("<!DOCTYPE html><html><body>x</body></html>")).toBe(true);
  });

  it("passes full documents through srcdoc unchanged", () => {
    const full = "<!DOCTYPE html><html><body>x</body></html>";
    expect(buildHtmlSnippetSrcDoc(full)).toBe(full);
  });

  it("decodes common entities", () => {
    expect(decodeHtmlSnippetEntities("&lt;script&gt;")).toBe("<script>");
  });

  it("sanitizes event handlers and javascript hrefs", () => {
    expect(sanitizeHtmlSnippet('<img src=x onerror="alert(1)">')).toBe('<img src="x">');
    expect(sanitizeHtmlSnippet('<a href="javascript:alert(1)">x</a>')).toBe("<a>x</a>");
    expect(sanitizeHtmlSnippet("<p>safe</p>")).toBe("<p>safe</p>");
  });

  it("blocks sanitizer bypass vectors", () => {
    const mangled = sanitizeHtmlSnippet("<scri<script>pt>alert(1)</scri</script>pt>");
    expect(mangled.toLowerCase()).not.toContain("<script");
    expect(sanitizeHtmlSnippet('<a href="&#106;avascript:alert(1)">x</a>')).toBe("<a>x</a>");
    expect(sanitizeHtmlSnippet('<a href="data:text/html,<b>1</b>">y</a>')).toBe("<a>y</a>");
    expect(sanitizeHtmlSnippet("<foreignObject><img src=x onerror=alert(1)></foreignObject>")).toBe("");
  });

  it("keeps style and form tags forbidden", () => {
    expect(sanitizeHtmlSnippet("<style>body{display:none}</style><p>x</p>")).toBe("<p>x</p>");
    expect(sanitizeHtmlSnippet('<form action="/x"><button>go</button></form>')).toBe("<button>go</button>");
  });

  it("parses single external iframe embeds before sanitization strips them", () => {
    expect(
      parseHtmlSnippetIframeEmbed(
        "<iframe src='https://ya.ru' style='width:100%;height:100%;border:none' title='Яндекс'></iframe>",
      ),
    ).toEqual({ src: "https://ya.ru", title: "Яндекс" });
    expect(parseHtmlSnippetIframeEmbed('<iframe src="javascript:alert(1)"></iframe>')).toBeNull();
    expect(parseHtmlSnippetIframeEmbed("<p>text</p><iframe src='https://ya.ru'></iframe>")).toBeNull();
    expect(sanitizeHtmlSnippet("<iframe src='https://ya.ru'></iframe>")).toBe("");
  });
});
