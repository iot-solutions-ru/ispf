import type { BindingRule } from "../types";
import { defaultBindingActivators } from "./bindingActivatorsUtils";

export function emptyBindingRule(): BindingRule {
  return {
    id: "",
    name: "",
    enabled: true,
    order: 0,
    activators: defaultBindingActivators(),
    condition: "",
    expression: "",
    target: { variableName: "", field: "value" },
  };
}

export function mergeBindingRules(rules: BindingRule[], rule: BindingRule): BindingRule[] {
  const next = rules.filter((item) => item.id !== rule.id);
  next.push(rule);
  next.sort((a, b) => a.order - b.order || a.id.localeCompare(b.id));
  return next;
}

export function isBindingRuleSaveable(rule: BindingRule): boolean {
  return Boolean(
    rule.id.trim()
    && rule.target.variableName.trim()
    && rule.expression.trim(),
  );
}

export function prepareBindingRuleForSave(rule: BindingRule): BindingRule {
  const trimmedId = rule.id.trim();
  return {
    ...rule,
    id: trimmedId,
    name: rule.name?.trim() || trimmedId,
    target: { ...rule.target, field: rule.target.field ?? "value" },
  };
}

export function isExistingBindingRule(rules: BindingRule[], ruleId: string): boolean {
  return rules.some((rule) => rule.id === ruleId);
}
