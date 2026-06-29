import type { RegisteredSymbol } from "../../scada/symbols/registry";

const PREVIEW_BOX = 40;

interface SymbolPreviewProps {
  symbol: RegisteredSymbol;
}

/** Mini SVG thumbnail for the symbol palette. */
export default function SymbolPreview({ symbol }: SymbolPreviewProps) {
  const Render = symbol.render;
  const w = symbol.defaultWidth;
  const h = symbol.defaultHeight;
  const pad = 3;
  const scale = Math.min((PREVIEW_BOX - pad * 2) / w, (PREVIEW_BOX - pad * 2) / h);
  const tx = (PREVIEW_BOX - w * scale) / 2;
  const ty = (PREVIEW_BOX - h * scale) / 2;

  return (
    <svg
      className="scada-palette-icon"
      viewBox={`0 0 ${PREVIEW_BOX} ${PREVIEW_BOX}`}
      aria-hidden
    >
      <rect
        x={0.5}
        y={0.5}
        width={PREVIEW_BOX - 1}
        height={PREVIEW_BOX - 1}
        rx={4}
        fill="var(--bg)"
        stroke="var(--border)"
      />
      <g transform={`translate(${tx},${ty}) scale(${scale})`}>
        <Render
          width={w}
          height={h}
          values={{}}
          props={symbol.paletteProps ?? {}}
          styleOverrides={{}}
          selected={false}
        />
      </g>
    </svg>
  );
}
