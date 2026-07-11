import { useMemo } from "react";
import type { HtmlSnippetWidget } from "../../../types/dashboard";
import DashWidgetShell from "../DashWidgetShell";
import { useWidgetStyles } from "../widgetStyles";
import {
  buildHtmlSnippetSrcDoc,
  htmlSnippetRequiresIframe,
  parseHtmlSnippetIframeEmbed,
  sanitizeHtmlSnippet,
} from "./htmlSnippetDocument";

interface HtmlSnippetWidgetViewProps {
  widget: HtmlSnippetWidget;
  editable?: boolean;
}

export default function HtmlSnippetWidgetView({ widget, editable }: HtmlSnippetWidgetViewProps) {
  const styles = useWidgetStyles(widget.stylesJson);
  const rawHtml = widget.htmlJson ?? "";
  const externalEmbed = useMemo(() => parseHtmlSnippetIframeEmbed(rawHtml), [rawHtml]);
  const html = sanitizeHtmlSnippet(rawHtml);
  const useIframe = useMemo(() => htmlSnippetRequiresIframe(html), [html]);
  const srcDoc = useMemo(() => (useIframe ? buildHtmlSnippetSrcDoc(html) : ""), [html, useIframe]);

  return (
    <DashWidgetShell
      title={widget.title}
      stylesJson={widget.stylesJson}
      className="dash-widget dash-widget-html"
      editable={editable}
    >
      {externalEmbed ? (
        <iframe
          className="dash-html-iframe"
          style={styles.body}
          title={externalEmbed.title || widget.title || "HTML snippet"}
          src={externalEmbed.src}
          sandbox="allow-scripts allow-same-origin allow-forms allow-popups allow-popups-to-escape-sandbox"
        />
      ) : useIframe ? (
        <iframe
          key={srcDoc}
          className="dash-html-iframe"
          style={styles.body}
          title={widget.title || "HTML snippet"}
          srcDoc={srcDoc}
          sandbox="allow-scripts"
        />
      ) : (
        <div
          className="dash-html-body"
          style={styles.body}
          dangerouslySetInnerHTML={{ __html: html }}
        />
      )}
    </DashWidgetShell>
  );
}
