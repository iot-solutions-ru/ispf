import { useQuery } from "@tanstack/react-query";
import { fetchPlatformMetrics, type PlatformMetricSection } from "../api/platformMetrics";

const METRIC_LABELS: Record<string, string> = {
  uptimeMs: "Uptime (мс)",
  uptimeHuman: "Uptime",
  heapUsedBytes: "Heap used (bytes)",
  heapMaxBytes: "Heap max (bytes)",
  heapUsedMb: "Heap used (MB)",
  heapMaxMb: "Heap max (MB)",
  nonHeapUsedMb: "Non-heap used (MB)",
  processors: "CPU (логические ядра)",
  threadCount: "Потоков JVM",
  peakThreadCount: "Пик потоков JVM",
  poolName: "Пул JDBC",
  activeConnections: "Активных соединений",
  idleConnections: "Idle соединений",
  totalConnections: "Всего соединений",
  threadsAwaitingConnection: "Ожидают соединение",
  maxPoolSize: "Max pool size",
  poolAvailable: "HikariCP доступен",
  objectNodes: "Узлов дерева",
  variables: "Переменных",
  devices: "Устройств (DEVICE)",
  dashboards: "Дашбордов",
  workflows: "Workflow-объектов",
  applications: "Приложений",
  models: "Моделей",
  alerts: "Alert-объектов",
  deviceObjects: "Устройств в дереве",
  activeDrivers: "Активных драйверов",
  connectedDrivers: "Подключённых драйверов",
  driversWithError: "Драйверов с ошибкой",
  stoppedDrivers: "Остановленных драйверов",
  websocketClients: "WebSocket клиентов",
  platformUsers: "Пользователей платформы",
  enabledUsers: "Активных пользователей",
  activeAuthSessions: "Активных сессий (token)",
  enabled: "Historian включён",
  minIntervalMs: "Debounce записи (мс)",
  defaultRetentionDays: "Retention по умолчанию (дн)",
  historizedVariables: "Переменных с historyEnabled",
  sampleCount: "Записей в variable_samples",
  oldestSampleAt: "Старейшая точка",
  newestSampleAt: "Последняя точка",
  eventHistoryRecords: "Событий в журнале",
  workflowInstancesTotal: "Workflow instances (всего)",
  workflowInstancesRunning: "Workflow RUNNING",
  workflowInstancesCompleted: "Workflow COMPLETED",
  workflowInstancesFailed: "Workflow FAILED",
  workflowInstancesCancelled: "Workflow CANCELLED",
  alertRules: "Правил алертов",
  eventCorrelators: "Корреляторов",
  applicationFunctions: "Функций приложений",
  applicationFunctionVersions: "Версий функций",
  platformSchedules: "Расписаний платформы",
  platformSchedulesEnabled: "Расписаний включено",
};

function formatMetricValue(value: unknown): string {
  if (value == null) {
    return "—";
  }
  if (typeof value === "boolean") {
    return value ? "да" : "нет";
  }
  if (typeof value === "number") {
    return Number.isInteger(value) ? String(value) : value.toLocaleString();
  }
  return String(value);
}

function MetricSectionCard({ section }: { section: PlatformMetricSection }) {
  const entries = Object.entries(section.values ?? {});

  return (
    <section className="system-metrics-card">
      <h3>{section.title}</h3>
      <table className="op-table system-metrics-table">
        <tbody>
          {entries.map(([key, value]) => (
            <tr key={key}>
              <th>{METRIC_LABELS[key] ?? key}</th>
              <td>
                {typeof value === "string" && value.includes("T") && value.endsWith("Z") ? (
                  <time dateTime={value}>{new Date(value).toLocaleString()}</time>
                ) : (
                  formatMetricValue(value)
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </section>
  );
}

export default function SystemMetricsView() {
  const metricsQuery = useQuery({
    queryKey: ["platform-metrics"],
    queryFn: fetchPlatformMetrics,
    refetchInterval: 30_000,
  });

  return (
    <main className="main system-metrics-view">
      <header className="system-metrics-header">
        <div>
          <h2>Система</h2>
          <p className="op-muted">
            Сводные метрики платформы для администратора. Внешний scrape Prometheus по-прежнему
            доступен на <code>/actuator/prometheus</code> (health-check).
          </p>
        </div>
        <button
          type="button"
          className="btn"
          disabled={metricsQuery.isFetching}
          onClick={() => metricsQuery.refetch()}
        >
          Обновить
        </button>
      </header>

      {metricsQuery.error && (
        <div className="op-alert op-alert-error">{String(metricsQuery.error)}</div>
      )}

      {metricsQuery.isLoading && <p className="hint">Загрузка метрик…</p>}

      {metricsQuery.data && (
        <>
          <p className="system-metrics-updated hint">
            Обновлено: {new Date(metricsQuery.data.timestamp).toLocaleString()}
          </p>
          <div className="system-metrics-grid">
            {metricsQuery.data.sections.map((section) => (
              <MetricSectionCard key={section.id} section={section} />
            ))}
          </div>
        </>
      )}
    </main>
  );
}
