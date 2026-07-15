/** Maps AggreGate topology params to ISPF object-tree paths (M11 pilot). */

export const SITE = "m11";
export const NETWORK = `root.platform.devices.itm.sites.${SITE}.network`;
export const ISP = `root.platform.devices.itm.sites.${SITE}.isp`;
export const SECTIONS = `root.platform.devices.itm.sites.${SITE}.sections`;

/** AggreGate node param name → inventory device id */
export const NODE_DEVICE_IDS = {
  TP14: "tp14",
  TP12: "tp12",
  TP16: "tp16",
  CPU5: "cpu5",
  DEU19: "deu19",
  DEU19TP12: "deu19tp12",
  TsPU5DEU19: "tspu5deu19",
  TP16TsPU5: "tp16tspu5",
  PKADTP16: "pkadtp16",
};

export const COLORS = {
  nodeOnline: "#97FEC6",
  nodeOffline: "#E57373",
  nodeUnknown: "#DBD7D4",
  linkUp: "#178E4E",
  linkDown: "#D32F2F",
};

/** Light HMI canvas — legacy AggreGate / Figma topology used a bright map, not dark UI chrome. */
export const TOPOLOGY_CANVAS = {
  diagramBackground: "#EEF2F6",
  svgBackground: "#EEF2F6",
  labelFill: "#1B3A4B",
};

/**
 * @param {string} linkName AggreGate link param name
 */
export function resolveLinkBinding(linkName) {
  if (linkName.includes("ROSTELECOM")) {
    return binding(`${ISP}.isp-rostelecom`, "linkStatus", "value");
  }
  if (linkName.includes("WESTCALL")) {
    return binding(`${ISP}.isp-megafon`, "linkStatus", "value");
  }
  if (/SECTION1/i.test(linkName)) {
    return binding(`${SECTIONS}.section1.sw-section1`, "status", "online");
  }
  if (/SECTION2/i.test(linkName)) {
    return binding(`${SECTIONS}.section2.sw-section2`, "status", "online");
  }
  if (/SECTION3/i.test(linkName)) {
    return binding(`${SECTIONS}.section3.sw-section3`, "status", "online");
  }
  if (/SECTION4/i.test(linkName)) {
    return binding(`${SECTIONS}.section4.sw-section4`, "status", "online");
  }
  if (/SECTION6/i.test(linkName)) {
    return binding(`${SECTIONS}.section6.sw-section6`, "status", "online");
  }
  if (/CPU5|BKTP|BTKP/i.test(linkName)) {
    return binding(`${NETWORK}.cpu5`, "status", "online");
  }
  if (/DEU19|DEU-19/i.test(linkName)) {
    return binding(`${NETWORK}.deu19`, "status", "online");
  }
  if (/TP12/i.test(linkName)) {
    return binding(`${NETWORK}.tp12`, "status", "online");
  }
  if (/TP16|CAD/i.test(linkName)) {
    return binding(`${NETWORK}.tp16`, "status", "online");
  }
  if (/TP14/i.test(linkName)) {
    return binding(`${NETWORK}.tp14`, "status", "online");
  }
  if (/TsPU5|TP16TsPU5/i.test(linkName)) {
    return binding(`${NETWORK}.tp16tspu5`, "status", "online");
  }
  if (/PKAD/i.test(linkName)) {
    return binding(`${NETWORK}.pkadtp16`, "status", "online");
  }
  return binding(`${NETWORK}.cpu5`, "status", "online");
}

/** @param {string} nodeName */
export function resolveNodeBinding(nodeName) {
  const deviceId = NODE_DEVICE_IDS[nodeName];
  if (!deviceId) {
    return binding(`${NETWORK}.cpu5`, "status", "online");
  }
  return binding(`${NETWORK}.${deviceId}`, "status", "online");
}

export function nodeDevicePath(nodeName) {
  const deviceId = NODE_DEVICE_IDS[nodeName];
  return deviceId ? `${NETWORK}.${deviceId}` : `${NETWORK}.cpu5`;
}

function binding(objectPath, variableName, valueField) {
  return { objectPath, variableName, valueField, transform: "bool" };
}

/** @typedef {{ name: string, kind: 'node'|'link', css: 'fill'|'stroke', targetId: string }} AggParam */

/** @param {string} svgText @returns {AggParam[]} */
export function parseAggParams(svgText) {
  const re =
    /<agg:param\s+name="([^"]+)"\s+description="[^"]*"\s+type="[^"]+"\s+cssAttributes="(fill|stroke)"\s+ids="([^"]+)"\s*\/>/g;
  /** @type {AggParam[]} */
  const params = [];
  let m;
  while ((m = re.exec(svgText)) !== null) {
    const [, name, css, targetId] = m;
    params.push({
      name,
      kind: css === "fill" ? "node" : "link",
      css,
      targetId,
    });
  }
  return params;
}

/** @param {string} svgText */
export function cleanSvgForIspf(svgText) {
  return svgText
    .replace(/xmlns:agg="[^"]*"\s*/g, "")
    .replace(/<agg:params>[\s\S]*?<\/agg:params>\s*/g, "")
    .replace(/\s+xml:space="preserve"/g, "");
}

/** @param {string} svgText */
export function extractSvgInner(svgText) {
  const cleaned = cleanSvgForIspf(svgText);
  const match = cleaned.match(/<svg[^>]*>([\s\S]*)<\/svg>\s*$/i);
  return match ? match[1].trim() : cleaned;
}

/** @param {string} decls */
function parseCssDeclarations(decls) {
  /** @type {Record<string, string>} */
  const out = {};
  for (const part of decls.split(";")) {
    const trimmed = part.trim();
    if (!trimmed) continue;
    const colon = trimmed.indexOf(":");
    if (colon < 0) continue;
    const prop = trimmed.slice(0, colon).trim().toLowerCase();
    const value = trimmed.slice(colon + 1).trim();
    if (prop && value) out[prop] = value;
  }
  return out;
}

const SVG_ATTRS = new Set([
  "fill",
  "stroke",
  "stroke-width",
  "stroke-opacity",
  "fill-opacity",
  "opacity",
  "clip-path",
]);

/**
 * ISPF scada renderer strips <style> tags (sanitizeSvgMarkup). Inline class rules
 * so Illustrator-exported topology keeps colors, strokes, and clip-paths.
 * @param {string} svgInner
 */
export function inlineSvgStyles(svgInner) {
  const styleMatch = svgInner.match(/<style[^>]*>([\s\S]*?)<\/style>/i);
  if (!styleMatch) return svgInner;

  /** @type {Record<string, Record<string, string>>} */
  const classRules = {};
  const ruleRe = /\.([a-zA-Z0-9_-]+)\s*\{([^}]+)\}/g;
  let m;
  while ((m = ruleRe.exec(styleMatch[1])) !== null) {
    classRules[m[1]] = parseCssDeclarations(m[2]);
  }

  let result = svgInner.replace(/<style[^>]*>[\s\S]*?<\/style>/gi, "");

  result = result.replace(
    /<([a-zA-Z][\w:-]*)([^>]*?)\bclass="([^"]+)"([^>]*)(\/?)>/g,
    (full, tag, before, classes, after, selfClose) => {
      /** @type {Record<string, string>} */
      const merged = {};
      for (const cls of classes.split(/\s+/)) {
        const rule = classRules[cls];
        if (rule) Object.assign(merged, rule);
      }
      if (Object.keys(merged).length === 0) return full;

      const attrs = `${before} ${after}`;
      const additions = [];
      for (const [prop, value] of Object.entries(merged)) {
        if (!SVG_ATTRS.has(prop)) continue;
        const attr = prop;
        const attrRe = new RegExp(`\\b${attr.replace(/-/g, "\\-")}\\s*=`, "i");
        if (attrRe.test(attrs)) continue;
        additions.push(`${attr}="${value}"`);
      }
      if (additions.length === 0) return full;
      return `<${tag}${before} class="${classes}" ${additions.join(" ")}${after}${selfClose ? "/" : ""}>`;
    }
  );

  return result;
}

const TOPOLOGY_VIEWBOX = { w: 1309, h: 503 };

/**
 * Brighten topology for operator HMI: light canvas + readable path-based labels.
 * @param {string} svgInner
 */
export function brightenTopologySvg(svgInner) {
  const bg = `<rect x="0" y="0" width="${TOPOLOGY_VIEWBOX.w}" height="${TOPOLOGY_VIEWBOX.h}" fill="${TOPOLOGY_CANVAS.svgBackground}" stroke="none"/>`;
  const labeled = svgInner.replace(
    /<path(?![^>]*\bclass=)(?![^>]*\bfill=)(?![^>]*\bid=)([^>]*?)>/gi,
    `<path fill="${TOPOLOGY_CANVAS.labelFill}"$1>`
  );
  return `${bg}\n${labeled}`;
}

/** @typedef {{ id: string, x: number, y: number, w: number, h: number, nodeName: string, objectPath?: string, kind?: string, label?: string }} TopologyHitArea */

/** @param {string} svgText @returns {TopologyHitArea[]} */
export function parseNodeHitAreas(svgText) {
  const re =
    /<rect\s+id="back_([^"]+)"\s+x="([^"]+)"\s+y="([^"]+)"[^>]*width="([^"]+)"[^>]*height="([^"]+)"/g;
  /** @type {TopologyHitArea[]} */
  const areas = [];
  let m;
  while ((m = re.exec(svgText)) !== null) {
    const [, suffix, x, y, w, h] = m;
    areas.push({
      id: `back_${suffix}`,
      nodeName: suffix,
      objectPath: nodeDevicePath(suffix),
      kind: "zone",
      label: suffix,
      x: Math.round(Number.parseFloat(x)),
      y: Math.round(Number.parseFloat(y)),
      w: Math.round(Number.parseFloat(w)),
      h: Math.round(Number.parseFloat(h)),
    });
  }
  return areas;
}

/**
 * Zone (back_*) + link (#link_*) hit areas for operator navigation.
 * @param {string} svgText
 * @param {AggParam[]} [aggParams]
 * @returns {TopologyHitArea[]}
 */
export function buildTopologyHitAreas(svgText, aggParams = parseAggParams(svgText)) {
  const zones = parseNodeHitAreas(svgText);
  const seenIds = new Set(zones.map((area) => area.id));
  /** @type {TopologyHitArea[]} */
  const links = [];
  for (const param of aggParams.filter((entry) => entry.kind === "link")) {
    const id = param.targetId?.replace(/^#/, "");
    if (!id || seenIds.has(id)) continue;
    seenIds.add(id);
    const resolved = resolveLinkBinding(param.name);
    links.push({
      id,
      nodeName: param.name,
      objectPath: resolved.objectPath,
      kind: "link",
      label: param.name.replace(/^link_/, "").replaceAll("_", " "),
      x: 0,
      y: 0,
      w: 0,
      h: 0,
    });
  }
  return [...zones, ...links];
}
