import { Badge, Button, Space, Typography } from "antd";
import { useTranslation } from "react-i18next";
import type { AlarmShelf } from "../../api";

export interface AlarmShelfPanelProps {
  shelves: AlarmShelf[];
  onUnshelve: (id: string) => void;
}

function formatExpiry(
  expiresAt: string | null | undefined,
  labels: { noExpiry: string; expired: string }
): string {
  if (!expiresAt) {
    return labels.noExpiry;
  }
  const ms = new Date(expiresAt).getTime() - Date.now();
  if (ms <= 0) {
    return labels.expired;
  }
  const minutes = Math.ceil(ms / 60_000);
  if (minutes < 60) {
    return `${minutes}m`;
  }
  const hours = Math.floor(minutes / 60);
  const rem = minutes % 60;
  return rem > 0 ? `${hours}h ${rem}m` : `${hours}h`;
}

export default function AlarmShelfPanel({ shelves, onUnshelve }: AlarmShelfPanelProps) {
  const { t } = useTranslation("operator");

  if (shelves.length === 0) {
    return null;
  }

  return (
    <aside className="operator-alarm-shelf-panel" data-testid="operator-alarm-shelves">
      <header className="operator-alarm-shelf-panel-head">
        <Typography.Text strong>{t("alarmShelves.title")}</Typography.Text>
        <Badge count={t("alarmShelves.count", { count: shelves.length })} />
      </header>
      <ul className="operator-alarm-shelf-list">
        {shelves.map((shelf) => (
          <li key={shelf.id} className="operator-alarm-shelf-item">
            <Space className="operator-alarm-shelf-main" direction="vertical" size={2}>
              <Typography.Text className="operator-alarm-shelf-event">{shelf.eventName}</Typography.Text>
              <Typography.Text className="operator-alarm-shelf-path">{shelf.objectPath}</Typography.Text>
              {shelf.comment && <Typography.Text className="hint operator-alarm-shelf-comment">{shelf.comment}</Typography.Text>}
              <Typography.Text className="hint operator-alarm-shelf-meta">
                {t("alarmShelves.by", { user: shelf.shelvedBy })} · {t("alarmShelves.expires", {
                  time: formatExpiry(shelf.expiresAt, {
                    noExpiry: t("alarmShelves.noExpiry"),
                    expired: t("alarmShelves.expired"),
                  }),
                })}
              </Typography.Text>
            </Space>
            <Button size="small" className="operator-alarm-shelf-unshelve" onClick={() => onUnshelve(shelf.id)}>
              {t("alarmShelves.unshelve")}
            </Button>
          </li>
        ))}
      </ul>
    </aside>
  );
}
