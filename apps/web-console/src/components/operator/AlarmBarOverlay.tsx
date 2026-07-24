import { Alert, Button, Space } from "antd";
import { useTranslation } from "react-i18next";
import type { ActiveOperatorAlarm } from "../../types/operatorAlarmBar";

export interface AlarmBarOverlayProps {
  enabled: boolean;
  position: "top" | "bottom";
  alarms: ActiveOperatorAlarm[];
  muted: boolean;
  toggleMute: () => void;
  onDismiss: (alarmId: string) => void;
  onShelve: (alarmId: string, durationMinutes?: number) => void;
  onOpenDashboard: (alarm: ActiveOperatorAlarm) => void;
  onOpenReport: (alarm: ActiveOperatorAlarm) => void;
  onOpenObject: (alarm: ActiveOperatorAlarm) => void;
  onPrimaryAction: (alarm: ActiveOperatorAlarm) => void;
  actionError?: string | null;
  clearActionError?: () => void;
}

function AlarmBarItem({
  alarm,
  showMute,
  muted,
  toggleMute,
  onDismiss,
  onShelve,
  onOpenDashboard,
  onOpenReport,
  onOpenObject,
  onPrimaryAction,
}: {
  alarm: ActiveOperatorAlarm;
  showMute: boolean;
  muted: boolean;
  toggleMute: () => void;
  onDismiss: () => void;
  onShelve: () => void;
  onOpenDashboard: () => void;
  onOpenReport: () => void;
  onOpenObject: () => void;
  onPrimaryAction: () => void;
}) {
  const { t } = useTranslation("operator");
  const isCritical = alarm.event.level === "CRITICAL";
  const levelClass = `operator-alarm-bar--level-${alarm.event.level.toLowerCase()}`;
  const { colors } = alarm;
  const showPrimaryAction = Boolean(alarm.primaryActionLabel && alarm.dashboardPath);
  const showSecondary = !alarm.hideSecondaryActions;
  const ackBlocked = alarm.ackRequired && !alarm.acknowledgeFunction;

  return (
    <div
      className={`operator-alarm-bar ${levelClass}${isCritical ? " operator-alarm-bar--critical" : ""}`}
      style={{
        background: colors.background,
        color: colors.text,
        borderColor: colors.border,
        ["--alarm-accent" as string]: colors.accent,
      }}
      role="alert"
      aria-live="assertive"
    >
      <div className="operator-alarm-bar-body">
        <div className="operator-alarm-bar-main">
          <span className="operator-alarm-bar-level">{alarm.event.level}</span>
          <strong className="operator-alarm-bar-title">{alarm.title}</strong>
        </div>
        <dl className="operator-alarm-bar-fields">
          {alarm.fieldRows.map((field) => (
            <div key={`${field.label}-${field.value}`} className="operator-alarm-bar-field">
              <dt>{field.label}</dt>
              <dd>{field.value}</dd>
            </div>
          ))}
        </dl>
      </div>
      <Space className="operator-alarm-bar-actions" wrap>
        {showPrimaryAction && (
          <Button type="primary" className="operator-alarm-bar-btn primary" onClick={onPrimaryAction}>
            {alarm.primaryActionLabel}
          </Button>
        )}
        {showSecondary && alarm.dashboardPath && (
          <Button className="operator-alarm-bar-btn" onClick={onOpenDashboard}>
            {t("alarmBar.dashboard")}
          </Button>
        )}
        {showSecondary && alarm.reportPath && (
          <Button className="operator-alarm-bar-btn" onClick={onOpenReport}>
            {t("alarmBar.report")}
          </Button>
        )}
        {showSecondary && (
          <Button className="operator-alarm-bar-btn" onClick={onOpenObject}>
            {t("alarmBar.toObject")}
          </Button>
        )}
        {showMute && (
          <Button
            className={`btn operator-alarm-bar-btn${muted ? " operator-alarm-bar-btn-muted" : ""}`}
            onClick={toggleMute}
            title={muted ? t("alarmBar.unmute") : t("alarmBar.mute")}
          >
            {muted ? "🔇" : "🔊"}
          </Button>
        )}
        {!alarm.hideAcknowledge && (
          <Button
            type="primary"
            className="btn primary operator-alarm-bar-btn"
            onClick={onDismiss}
            disabled={ackBlocked}
            title={ackBlocked ? t("alarmBar.ackRequiredMissingFunction") : undefined}
          >
            {t("alarmBar.acknowledge")}
          </Button>
        )}
        <Button className="operator-alarm-bar-btn" onClick={onShelve}>
          {t("alarmBar.shelve")}
        </Button>
      </Space>
    </div>
  );
}

export default function AlarmBarOverlay({
  enabled,
  position,
  alarms,
  muted,
  toggleMute,
  onDismiss,
  onShelve,
  onOpenDashboard,
  onOpenReport,
  onOpenObject,
  onPrimaryAction,
  actionError,
  clearActionError,
}: AlarmBarOverlayProps) {
  if (!enabled || alarms.length === 0) {
    return null;
  }

  return (
    <div
      className={`operator-alarm-bar-stack operator-alarm-bar-stack--${position}`}
      data-testid="operator-alarm-bar"
    >
      {actionError && (
        <Alert
          className="operator-alarm-bar-error"
          type="error"
          message={actionError}
          role="status"
          closable={Boolean(clearActionError)}
          onClose={clearActionError}
        />
      )}
      {alarms.map((alarm, index) => (
        <AlarmBarItem
          key={alarm.id}
          alarm={alarm}
          showMute={index === 0}
          muted={muted}
          toggleMute={toggleMute}
          onDismiss={() => onDismiss(alarm.id)}
          onShelve={() => onShelve(alarm.id, 60)}
          onOpenDashboard={() => onOpenDashboard(alarm)}
          onOpenReport={() => onOpenReport(alarm)}
          onOpenObject={() => onOpenObject(alarm)}
          onPrimaryAction={() => onPrimaryAction(alarm)}
        />
      ))}
    </div>
  );
}
