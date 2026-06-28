import type { TFunction } from "i18next";
import {
  Bar,
  CartesianGrid,
  ComposedChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import type { CandlestickPoint } from "../../../utils/chartOhlcUtils";

const DEFAULT_DOWN_COLOR = "#f85149";

interface CandlestickBarShapeProps {
  x?: number;
  width?: number;
  payload?: CandlestickPoint;
  yAxis?: { scale?: (value: number) => number };
  upColor: string;
  downColor: string;
}

function CandlestickBarShape({
  x = 0,
  width = 0,
  payload,
  yAxis,
  upColor,
  downColor,
}: CandlestickBarShapeProps) {
  if (!payload || !yAxis?.scale) {
    return null;
  }
  const scale = yAxis.scale;
  const { open, high, low, close } = payload;
  const yHigh = scale(high);
  const yLow = scale(low);
  const yOpen = scale(open);
  const yClose = scale(close);
  const bodyTop = Math.min(yOpen, yClose);
  const bodyBottom = Math.max(yOpen, yClose);
  const bodyHeight = Math.max(bodyBottom - bodyTop, 1);
  const cx = x + width / 2;
  const bullish = close >= open;
  const color = bullish ? upColor : downColor;

  return (
    <g>
      <line x1={cx} x2={cx} y1={yHigh} y2={yLow} stroke={color} strokeWidth={1.5} />
      <rect
        x={x + width * 0.15}
        y={bodyTop}
        width={width * 0.7}
        height={bodyHeight}
        fill={bullish ? color : "transparent"}
        stroke={color}
        strokeWidth={1.5}
      />
    </g>
  );
}

function CandlestickTooltipContent({
  active,
  payload,
  label,
  decimals,
  unit,
  t,
}: {
  active?: boolean;
  payload?: Array<{ payload?: CandlestickPoint }>;
  label?: string;
  decimals: number;
  unit: string;
  t: TFunction<"widgets">;
}) {
  if (!active || !payload?.length) {
    return null;
  }
  const point = payload[0]?.payload;
  if (!point) {
    return null;
  }
  const rows: Array<{ key: "open" | "high" | "low" | "close"; label: string }> = [
    { key: "open", label: t("view.chartCandlestick.open") },
    { key: "high", label: t("view.chartCandlestick.high") },
    { key: "low", label: t("view.chartCandlestick.low") },
    { key: "close", label: t("view.chartCandlestick.close") },
  ];
  return (
    <div
      style={{
        background: "#161b22",
        border: "1px solid #30363d",
        borderRadius: 8,
        padding: "8px 10px",
        fontSize: 12,
      }}
    >
      <div style={{ marginBottom: 6, color: "#8b949e" }}>
        {t("view.timeLabel", { label: label ?? "" })}
      </div>
      {rows.map(({ key, label: name }) => (
        <div key={key} style={{ display: "flex", gap: 8, justifyContent: "space-between" }}>
          <span>{name}</span>
          <span>
            {point[key].toFixed(decimals)}
            {unit ? ` ${unit}` : ""}
          </span>
        </div>
      ))}
    </div>
  );
}

interface CandlestickChartBodyProps {
  data: CandlestickPoint[];
  decimals: number;
  unit: string;
  upColor: string;
  downColor?: string;
  t: TFunction<"widgets">;
}

export default function CandlestickChartBody({
  data,
  decimals,
  unit,
  upColor,
  downColor = DEFAULT_DOWN_COLOR,
  t,
}: CandlestickChartBodyProps) {
  return (
    <ResponsiveContainer width="100%" height="100%">
      <ComposedChart data={data} margin={{ top: 8, right: 8, left: 0, bottom: 0 }}>
        <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.08)" />
        <XAxis dataKey="time" tick={{ fontSize: 10 }} minTickGap={24} />
        <YAxis
          tick={{ fontSize: 10 }}
          width={42}
          domain={["auto", "auto"]}
          tickFormatter={(value) => Number(value).toFixed(decimals)}
        />
        <Tooltip
          content={
            <CandlestickTooltipContent decimals={decimals} unit={unit} t={t} />
          }
        />
        <Bar
          dataKey="close"
          barSize={12}
          isAnimationActive={false}
          shape={(props) => (
            <CandlestickBarShape {...props} upColor={upColor} downColor={downColor} />
          )}
        />
      </ComposedChart>
    </ResponsiveContainer>
  );
}
