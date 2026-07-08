import { useMemo } from "react";
import type { HtmlSnippetWidget } from "../../../types/dashboard";
import DashWidgetShell from "../DashWidgetShell";
import { useWidgetStyles } from "../widgetStyles";
import { buildHtmlSnippetSrcDoc, htmlSnippetRequiresIframe, sanitizeHtmlSnippet } from "./htmlSnippetDocument";

interface HtmlSnippetWidgetViewProps {
  widget: HtmlSnippetWidget;
  editable?: boolean;
}

export default function HtmlSnippetWidgetView({ widget, editable }: HtmlSnippetWidgetViewProps) {
  const styles = useWidgetStyles(widget.stylesJson);
  const html = sanitizeHtmlSnippet(widget.htmlJson ?? "");
  const useIframe = useMemo(() => htmlSnippetRequiresIframe(html), [html]);
  const srcDoc = useMemo(() => (useIframe ? buildHtmlSnippetSrcDoc(html) : ""), [html, useIframe]);

  return (
    <DashWidgetShell
      title={widget.title}
      stylesJson={widget.stylesJson}
      className="dash-widget dash-widget-html"
      editable={editable}
    >
      {useIframe ? (
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
