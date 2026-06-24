import { describe, expect, it } from "vitest";
import {
  buildHtmlSnippetSrcDoc,
  decodeHtmlSnippetEntities,
  htmlSnippetContainsScript,
  htmlSnippetRequiresIframe,
  isFullHtmlDocument,
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
});
