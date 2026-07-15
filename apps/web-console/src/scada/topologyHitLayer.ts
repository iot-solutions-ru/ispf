/** Keep painted SVG order; put invisible hit proxies on top for clicks. */
export function prepareTopologyHitLayer(
  root: Element,
  hitAreas: Array<{ id?: string; nodeName: string; kind?: string }>
): void {
  for (const el of root.querySelectorAll<SVGElement>("*")) {
    el.style.pointerEvents = "none";
  }

  for (const old of root.querySelectorAll("[data-topology-hit-proxy]")) {
    old.remove();
  }

  for (const area of hitAreas) {
    const targetId = area.id ?? `back_${area.nodeName}`;
    const el = root.querySelector(`#${CSS.escape(targetId)}`) as SVGElement | null;
    if (!el) continue;

    const kind =
      area.kind ?? (targetId.startsWith("link_") || targetId.startsWith("line_") ? "link" : "zone");
    el.setAttribute("data-topology-hit-source", kind);
    el.style.pointerEvents = "none";

    const proxy = el.cloneNode(true) as SVGElement;
    proxy.removeAttribute("id");
    proxy.setAttribute("data-topology-hit-proxy", targetId);
    proxy.setAttribute("data-topology-hit", kind);
    proxy.style.cursor = "pointer";

    if (kind === "link") {
      // Wide invisible stroke so thin cables are easy to click.
      proxy.style.pointerEvents = "stroke";
      proxy.style.fill = "none";
      proxy.style.stroke = "rgba(0,0,0,0.001)";
      proxy.style.strokeWidth = "14";
      proxy.style.strokeOpacity = "1";
    } else {
      // Invisible fill twin — original zone stay in paint order underneath.
      proxy.style.pointerEvents = "all";
      proxy.style.fill = "rgba(0,0,0,0.001)";
      proxy.style.fillOpacity = "1";
      proxy.style.stroke = "none";
      proxy.style.opacity = "1";
    }

    root.appendChild(proxy);
  }
}
