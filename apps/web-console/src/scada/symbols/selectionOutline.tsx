export function selectionOutline(selected?: boolean, width?: number, height?: number) {
  if (!selected || width == null || height == null) return null;
  return (
    <rect
      x={-4}
      y={-4}
      width={width + 8}
      height={height + 8}
      fill="none"
      stroke="var(--accent)"
      strokeWidth={2}
      strokeDasharray="4 2"
      pointerEvents="none"
    />
  );
}
