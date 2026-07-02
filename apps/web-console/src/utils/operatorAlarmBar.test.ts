import { describe, expect, it } from "vitest";
import type { ObjectEvent } from "../types/event";
import type { OperatorAlarmRule } from "../types/operatorAlarmBar";
import { buildActiveAlarm, resolveAlarmBarConfig, resolveAlarmNavigateParams } from "./operatorAlarmBar";

function event(partial: Partial<ObjectEvent> & Pick<ObjectEvent, "eventName" | "level">): ObjectEvent {
  return {
    id: partial.id ?? "evt-1",
    objectPath: partial.objectPath ?? "root.platform.devices.ogp-mes-hub",
    eventName: partial.eventName,
    level: partial.level,
    payload: partial.payload ?? { schema: {}, rows: [] },
    timestamp: partial.timestamp ?? "2026-06-24T12:00:00Z",
  };
}

describe("resolveAlarmNavigateParams", () => {
  it("merges static session params with payload fields", () => {
    const rule: OperatorAlarmRule = {
      id: "ogp",
      actions: {
        sessionParams: { activeTab: "registration" },
        sessionParamsFromPayload: {
          unprocessedId: "unprocessedId",
          signalLabel: "signalLabel",
        },
      },
    };
    const evt = event({
      eventName: "ogpUnprocessedEvent",
      level: "WARNING",
      payload: {
        schema: {},
        rows: [{ unprocessedId: "abc-123", signalLabel: "Останов" }],
      },
    });
    expect(resolveAlarmNavigateParams(evt, rule)).toEqual({
      activeTab: "registration",
      unprocessedId: "abc-123",
      signalLabel: "Останов",
    });
  });
});

describe("buildActiveAlarm", () => {
  it("exposes register action metadata from rule", () => {
    const rule: OperatorAlarmRule = {
      id: "ogp",
      title: "Register event",
      actions: {
        dashboardPath: "root.platform.dashboards.ogp-operator-hmi",
        primaryActionLabel: "Зарегистрировать",
        hideSecondaryActions: true,
        hideAcknowledge: true,
        sessionParams: { activeTab: "registration" },
      },
    };
    const active = buildActiveAlarm(
      event({ eventName: "ogpUnprocessedEvent", level: "WARNING" }),
      rule,
      resolveAlarmBarConfig({ enabled: true, rules: [rule] })
    );
    expect(active.primaryActionLabel).toBe("Зарегистрировать");
    expect(active.hideSecondaryActions).toBe(true);
    expect(active.hideAcknowledge).toBe(true);
    expect(active.navigateParams).toEqual({ activeTab: "registration" });
  });

  it("forces acknowledge visible when alert rule requires ack", () => {
    const rule: OperatorAlarmRule = {
      id: "demo",
      actions: { hideAcknowledge: true },
    };
    const evt = event({ eventName: "thresholdExceeded", level: "HIGH" });
    const active = buildActiveAlarm(
      evt,
      rule,
      resolveAlarmBarConfig({ enabled: true, rules: [rule] }),
      undefined,
      [
        {
          id: "rule-1",
          name: "demo",
          objectPath: evt.objectPath,
          watchVariable: "temperature",
          conditionExpr: "true",
          eventName: "thresholdExceeded",
          payloadVariable: null,
          enabled: true,
          edgeTrigger: true,
          ackRequired: true,
          lastConditionMet: false,
          createdAt: "",
          updatedAt: "",
        },
      ]
    );
    expect(active.ackRequired).toBe(true);
    expect(active.hideAcknowledge).toBe(false);
  });
});
