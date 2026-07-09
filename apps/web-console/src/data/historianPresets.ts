import type { BindingRule } from "../types";
import { emptyHistorianRule } from "../components/bindingRulesUtils";

export interface HistorianPresetTemplate {
  id: string;
  label: string;
  rule: BindingRule;
}

export function historianRuleTemplates(
  objectPath: string,
  variableNames: string[]
): HistorianPresetTemplate[] {
  const source = variableNames.find((name) => name === "temperature") ?? variableNames[0] ?? "temperature";
  return [
    {
      id: "rolling-avg",
      label: "Rolling average (5m)",
      rule: {
        ...emptyHistorianRule(),
        id: "rolling-avg",
        name: "Rolling average",
        expression: `hist.avg('${objectPath}', '${source}', '5m')`,
        target: { kind: "variable", variableName: "avgValue", field: "value" },
        windowBucket: "5m",
      },
    },
    {
      id: "rate-of-change",
      label: "Rate of change (1h)",
      rule: {
        ...emptyHistorianRule(),
        id: "rate-of-change",
        name: "Rate of change",
        expression: `rateOfChange(${objectPath}.${source}, 1h)`,
        target: { kind: "variable", variableName: "rocValue", field: "value" },
        windowBucket: "1h",
        activators: {
          onStartup: false,
          onVariableChange: [{ objectPath, variableName: source }],
          onEvent: null,
          periodicMs: 60_000,
        },
      },
    },
    {
      id: "custom-cel",
      label: "Custom CEL (hist.*)",
      rule: {
        ...emptyHistorianRule(),
        id: "custom-cel",
        name: "Custom CEL",
        expression: `hist.avg('${objectPath}', '${source}', '5m')`,
        target: { kind: "variable", variableName: "computedValue", field: "value" },
      },
    },
  ];
}
