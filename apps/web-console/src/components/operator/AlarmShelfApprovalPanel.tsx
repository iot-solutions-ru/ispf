import { useCallback, useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import {
  approveAlarmShelfRequest,
  fetchAlarmShelfRequests,
  rejectAlarmShelfRequest,
  type AlarmShelfPendingRequest,
} from "../../api";

export interface AlarmShelfApprovalPanelProps {
  enabled: boolean;
}

export default function AlarmShelfApprovalPanel({ enabled }: AlarmShelfApprovalPanelProps) {
  const { t } = useTranslation("operator");
  const [requests, setRequests] = useState<AlarmShelfPendingRequest[]>([]);
  const [busyId, setBusyId] = useState<string | null>(null);

  const refresh = useCallback(() => {
    if (!enabled) {
      setRequests([]);
      return;
    }
    void fetchAlarmShelfRequests()
      .then(setRequests)
      .catch(() => setRequests([]));
  }, [enabled]);

  useEffect(() => {
    refresh();
    if (!enabled) {
      return;
    }
    const timer = window.setInterval(refresh, 30_000);
    return () => window.clearInterval(timer);
  }, [enabled, refresh]);

  const handleApprove = useCallback(
    async (id: string) => {
      setBusyId(id);
      try {
        await approveAlarmShelfRequest(id);
        setRequests((current) => current.filter((item) => item.id !== id));
      } catch {
        refresh();
      } finally {
        setBusyId(null);
      }
    },
    [refresh]
  );

  const handleReject = useCallback(
    async (id: string) => {
      setBusyId(id);
      try {
        await rejectAlarmShelfRequest(id);
        setRequests((current) => current.filter((item) => item.id !== id));
      } catch {
        refresh();
      } finally {
        setBusyId(null);
      }
    },
    [refresh]
  );

  if (!enabled || requests.length === 0) {
    return null;
  }

  return (
    <aside className="operator-alarm-shelf-panel operator-alarm-shelf-approval-panel" data-testid="operator-alarm-shelf-requests">
      <header className="operator-alarm-shelf-panel-head">
        <strong>{t("alarmShelves.approvalTitle")}</strong>
        <span className="hint">{t("alarmShelves.approvalCount", { count: requests.length })}</span>
      </header>
      <ul className="operator-alarm-shelf-list">
        {requests.map((request) => (
          <li key={request.id} className="operator-alarm-shelf-item">
            <div className="operator-alarm-shelf-main">
              <span className="operator-alarm-shelf-event">{request.eventName}</span>
              <span className="operator-alarm-shelf-path">{request.objectPath}</span>
              {request.comment && <span className="hint operator-alarm-shelf-comment">{request.comment}</span>}
              <span className="hint operator-alarm-shelf-meta">
                {t("alarmShelves.requestedBy", { user: request.requestedBy })}
              </span>
            </div>
            <div className="operator-alarm-shelf-actions">
              <button
                type="button"
                className="btn small operator-alarm-shelf-approve"
                disabled={busyId === request.id}
                onClick={() => void handleApprove(request.id)}
              >
                {t("alarmShelves.approve")}
              </button>
              <button
                type="button"
                className="btn small operator-alarm-shelf-reject"
                disabled={busyId === request.id}
                onClick={() => void handleReject(request.id)}
              >
                {t("alarmShelves.reject")}
              </button>
            </div>
          </li>
        ))}
      </ul>
    </aside>
  );
}
