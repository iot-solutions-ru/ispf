import { useMutation } from "@tanstack/react-query";
import { validateExpression } from "../api";

interface BindingExpressionFieldProps {
  id?: string;
  value: string;
  onChange: (value: string) => void;
  placeholder?: string;
  disabled?: boolean;
}

export default function BindingExpressionField({
  id,
  value,
  onChange,
  placeholder = "CEL или platform: selectField, scale, clamp, format, delta, counterRate, rate, movingAvg, refAt, callFunction",
  disabled = false,
}: BindingExpressionFieldProps) {
  const validateMutation = useMutation({
    mutationFn: () => validateExpression(value.trim()),
  });

  return (
    <div className="binding-expression-field">
      <input
        id={id}
        type="text"
        className="mono"
        value={value}
        disabled={disabled}
        placeholder={placeholder}
        onChange={(e) => onChange(e.target.value)}
      />
      <button
        type="button"
        className="btn small"
        disabled={disabled || !value.trim() || validateMutation.isPending}
        onClick={() => validateMutation.mutate()}
      >
        Проверить
      </button>
      {validateMutation.data?.valid && (
        <span className="hint success">Выражение корректно</span>
      )}
      {validateMutation.data && !validateMutation.data.valid && (
        <span className="hint error">{validateMutation.data.error ?? "Ошибка"}</span>
      )}
      {validateMutation.error && (
        <span className="hint error">{(validateMutation.error as Error).message}</span>
      )}
    </div>
  );
}
