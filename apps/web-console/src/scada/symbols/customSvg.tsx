import { useMemo } from "react";
import type { MimicSymbolBehavior, SymbolRenderProps } from "../../types/scadaMimic";
import { parseViewBoxString, replaceCssVars, sanitizeSvgMarkup } from "../customSvg";
import { applySvgBehaviors } from "../svgSymbolEngine";
import { selectionOutline } from "./process";

function readBehaviors(props: Record<string, unknown>): MimicSymbolBehavior[] | undefined {
  const raw = props.behaviors;
  if (!Array.isArray(raw)) return undefined;
  return raw as MimicSymbolBehavior[];
}

export function CustomSvgSymbol({
  width,
  height,
  values,
  props,
  styleOverrides,
  selected,
  onClick,
}: SymbolRenderProps) {
  const vb = parseViewBoxString(String(props.viewBox ?? ""), width, height);
  const sx = width / vb.w;
  const sy = height / vb.h;

  const svgInner = useMemo(() => {
    const raw = String(props.svg ?? "");
    const behaviors = readBehaviors(props);
    if (!behaviors?.length && !Object.keys(styleOverrides).length) {
      return sanitizeSvgMarkup(replaceCssVars(raw));
    }
    return applySvgBehaviors({
      svg: raw,
      values,
      behaviors,
      styleOverrides,
      props,
    });
  }, [props, values, styleOverrides]);

  return (
    <g onClick={onClick} style={{ cursor: onClick ? "pointer" : undefined }}>
      <g transform={`scale(${sx}, ${sy})`}>
        {svgInner ? (
          <g dangerouslySetInnerHTML={{ __html: svgInner }} />
        ) : (
          <rect
            x={0}
            y={0}
            width={vb.w}
            height={vb.h}
            fill="#161b22"
            stroke="#30363d"
            strokeWidth={1.5}
            strokeDasharray="4 2"
          />
        )}
      </g>
      {selectionOutline(selected, width, height)}
    </g>
  );
}
