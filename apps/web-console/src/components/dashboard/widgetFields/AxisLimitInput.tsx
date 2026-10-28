import { useEffect, useRef, useState } from "react";
import { WidgetEditorCaption } from "./widgetFieldPrimitives";

function formatAxisLimit(value: number | undefined): string {
  return typeof value === "number" && Number.isFinite(value) ? String(value) : "";
}

export default function AxisLimitInput({
  label,
  value,
  onChange,
}: {
  label: string;
  value: number | undefined;
  onChange: (next: number | undefined) => void;
}) {
  const [draft, setDraft] = useState(() => formatAxisLimit(value));
  const committed = useRef(value);

  useEffect(() => {
    if (Object.is(value, committed.current)) {
      return;
    }
    committed.current = value;
    setDraft(formatAxisLimit(value));
  }, [value]);

  return (
    <label>
      <WidgetEditorCaption text={label} />
      <input
        type="text"
        inputMode="decimal"
        value={draft}
        onChange={(event) => {
          const raw = event.target.value;
          if (raw !== "" && !/^-?\d*\.?\d*$/.test(raw)) {
            return;
          }
          setDraft(raw);
          if (raw.trim() === "" || raw === "-" || raw === "." || raw === "-.") {
            if (raw.trim() === "") {
              committed.current = undefined;
              onChange(undefined);
            }
            return;
          }
          const parsed = Number(raw);
          if (Number.isFinite(parsed)) {
            committed.current = parsed;
            onChange(parsed);
          }
        }}
      />
    </label>
  );
}
