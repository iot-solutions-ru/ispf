package com.ispf.server.platform;

import com.ispf.server.config.ClusterProperties;
import com.ispf.server.config.NatsProperties;
import com.ispf.server.driver.DriverRuntimeService;
import com.ispf.server.function.MqttGatewayIngressDispatchService;
import com.ispf.server.object.BindingPeriodicScheduleRegistry;
import com.ispf.server.object.RuntimeTelemetryCoalescer;
import com.ispf.server.object.TelemetryIngressDispatcher;
import com.ispf.server.persistence.WorkflowInstanceRepository;
import com.ispf.server.persistence.entity.WorkflowInstanceEntity;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import javax.sql.DataSource;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PlatformDiagnosticsService {

    private static final double CPU_HIGH = 70.0;
    private static final int OBJECT_CHANGE_QUEUE_WARN = 500;
    private static final int EVENT_JOURNAL_QUEUE_WARN = 200;
    private static final int VARIABLE_HISTORY_QUEUE_WARN = 200;
    private static final int IO_POLL_QUEUE_WARN = 50;
    private static final int DRIVER_PRESSURE_SUSPECT_MIN = 25;
    private static final long LONG_JOB_SECONDS = 300;
    private static final long LONG_WORKFLOW_SECONDS = 600;

    private final NatsProperties natsProperties;
    private final ClusterProperties clusterProperties;
    private final AutomationMetricsRecorder automationMetricsRecorder;
    private final DriverRuntimeService driverRuntimeService;
    private final PlatformJobService platformJobService;
    private final WorkflowInstanceRepository workflowInstanceRepository;
    private final PlatformMetricsService platformMetricsService;
    private final ObjectMapper objectMapper;
    private final DataSource dataSource;
    private final BindingPeriodicScheduleRegistry periodicScheduleRegistry;
    private final TelemetryIngressDispatcher telemetryIngressDispatcher;
    private final MqttGatewayIngressDispatchService mqttGatewayIngressDispatchService;
    private final RuntimeTelemetryCoalescer runtimeTelemetryCoalescer;
    private final int serverPort;

    private final Map<Long, Long> previousThreadCpuNanos = new ConcurrentHashMap<>();
    private volatile Instant previousThreadSampleAt;

    public PlatformDiagnosticsService(
            NatsProperties natsProperties,
            ClusterProperties clusterProperties,
            AutomationMetricsRecorder automationMetricsRecorder,
            DriverRuntimeService driverRuntimeService,
            PlatformJobService platformJobService,
            WorkflowInstanceRepository workflowInstanceRepository,
            PlatformMetricsService platformMetricsService,
            ObjectMapper objectMapper,
            DataSource dataSource,
            BindingPeriodicScheduleRegistry periodicScheduleRegistry,
            TelemetryIngressDispatcher telemetryIngressDispatcher,
            MqttGatewayIngressDispatchService mqttGatewayIngressDispatchService,
            RuntimeTelemetryCoalescer runtimeTelemetryCoalescer,
            @Value("${server.port:8080}") int serverPort
    ) {
        this.natsProperties = natsProperties;
        this.clusterProperties = clusterProperties;
        this.automationMetricsRecorder = automationMetricsRecorder;
        this.driverRuntimeService = driverRuntimeService;
        this.platformJobService = platformJobService;
        this.workflowInstanceRepository = workflowInstanceRepository;
        this.platformMetricsService = platformMetricsService;
        this.objectMapper = objectMapper;
        this.dataSource = dataSource;
        this.periodicScheduleRegistry = periodicScheduleRegistry;
        this.telemetryIngressDispatcher = telemetryIngressDispatcher;
        this.mqttGatewayIngressDispatchService = mqttGatewayIngressDispatchService;
        this.runtimeTelemetryCoalescer = runtimeTelemetryCoalescer;
        this.serverPort = serverPort;
    }

    @PostConstruct
    void enableThreadCpuMonitoring() {
        ThreadMXBean threads = ManagementFactory.getThreadMXBean();
        if (threads.isThreadCpuTimeSupported()) {
            threads.setThreadCpuTimeEnabled(true);
        }
    }

    public Map<String, Object> snapshot() {
        Map<String, Object> metrics = platformMetricsService.snapshotInternal();
        Map<String, Object> automation = castMap(metrics.get("automation"));
        Map<String, Object> runtime = castMap(metrics.get("runtime"));
        Map<String, Object> database = castMap(metrics.get("database"));
        Map<String, Object> drivers = castMap(metrics.get("drivers"));

        double processCpuPercent = processCpuPercent();
        double systemCpuPercent = systemCpuPercent();
        Double heapUsedPercent = heapUsedPercent(runtime);

        Map<String, Object> detail = buildDetail(automation, drivers);
        List<Map<String, Object>> suspects = buildSuspects(
                processCpuPercent,
                heapUsedPercent,
                automation,
                database,
                drivers,
                detail
        );

        Map<String, Object> diagnostics = new LinkedHashMap<>();
        diagnostics.put("replicaId", natsProperties.replicaId());
        diagnostics.put("replicaProfile", clusterProperties.effectiveCapabilities().profile().externalName());
        diagnostics.put("serverPort", serverPort);
        diagnostics.put("processCpuPercent", round1(processCpuPercent));
        diagnostics.put("systemCpuPercent", systemCpuPercent >= 0 ? round1(systemCpuPercent) : null);
        diagnostics.put("heapUsedPercent", heapUsedPercent != null ? round1(heapUsedPercent) : null);
        diagnostics.put("pressureScore", pressureScore(processCpuPercent, automation, drivers));
        diagnostics.put("suspects", suspects);
        diagnostics.put("detail", detail);
        if (!suspects.isEmpty()) {
            Map<String, Object> top = suspects.getFirst();
            diagnostics.put("topSuspect", Map.of(
                    "kind", top.get("kind"),
                    "title", top.get("title"),
                    "detail", top.get("detail"),
                    "ref", top.getOrDefault("ref", "")
            ));
        }
        return diagnostics;
    }

    private Map<String, Object> buildDetail(Map<String, Object> automation, Map<String, Object> drivers) {
        Map<String, Object> detail = new LinkedHashMap<>();
        Map<String, Object> threadCpu = threadCpuDiagnostics();
        detail.putAll(threadCpu);
        if (clusterProperties.hasCapability(com.ispf.server.config.ReplicaCapability.DRIVERS)) {
            detail.putAll(driverRuntimeService.driverDiagnosticsSnapshot());
        } else {
            detail.put("drivers", List.of());
        }
        detail.put("runningJobs", runningJobs());
        detail.put("runningWorkflows", runningWorkflows());
        detail.put("queues", queueSummary(automation, drivers));
        detail.put("bindingPeriodicRuleCount", periodicScheduleRegistry.countEnabled());
        Instant nextWake = periodicScheduleRegistry.nextWakeAt();
        detail.put("bindingPeriodicNextWakeAt", nextWake != null ? nextWake.toString() : null);
        detail.put("telemetryIngressStarted", telemetryIngressDispatcher.isStarted());
        detail.put("mqttGatewayIngressStarted", mqttGatewayIngressDispatchService.isWorkersStarted());
        detail.put("runtimeTelemetryCoalescerStarted", runtimeTelemetryCoalescer.isSchedulerStarted());
        return detail;
    }

    private Map<String, Object> queueSummary(Map<String, Object> automation, Map<String, Object> drivers) {
        Map<String, Object> queues = new LinkedHashMap<>();
        queues.put("objectChangeQueueSize", automation.get("objectChangeQueueSize"));
        queues.put("objectChangeQueueByLane", automation.get("objectChangeQueueByLane"));
        queues.put("objectChangeWorkersByLane", automation.get("objectChangeWorkersByLane"));
        queues.put("objectChangeDroppedTotal", automation.get("objectChangeDroppedTotal"));
        queues.put("eventJournalQueueSize", automation.get("eventJournalQueueSize"));
        queues.put("variableHistoryQueueSize", automation.get("variableHistoryQueueSize"));
        queues.put("activeDrivers", drivers.get("activeDrivers"));
        return queues;
    }

    private List<Map<String, Object>> runningJobs() {
        return platformJobService.listRunningByHolder(natsProperties.replicaId()).stream()
                .map(job -> {
                    Map<String, Object> row = job.toApiMap(objectMapper);
                    if (job.startedAt() != null) {
                        row.put("runningSeconds", Duration.between(job.startedAt(), Instant.now()).getSeconds());
                    }
                    Map<String, Object> payload = platformJobService.readPayloadMap(job);
                    Object path = payload.get("path");
                    if (path != null) {
                        row.put("reportPath", path.toString());
                    }
                    return row;
                })
                .toList();
    }

    private List<Map<String, Object>> runningWorkflows() {
        Instant now = Instant.now();
        return workflowInstanceRepository.findByStatusOrderByStartedAtDesc("RUNNING").stream()
                .limit(10)
                .map(instance -> workflowRow(instance, now))
                .toList();
    }

    private static Map<String, Object> workflowRow(WorkflowInstanceEntity instance, Instant now) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("instanceId", instance.getId());
        row.put("workflowPath", instance.getWorkflowPath());
        row.put("currentNodeId", instance.getCurrentNodeId());
        row.put("startedAt", instance.getStartedAt().toString());
        row.put("runningSeconds", Duration.between(instance.getStartedAt(), now).getSeconds());
        return row;
    }

    private Map<String, Object> threadCpuDiagnostics() {
        ThreadMXBean threads = ManagementFactory.getThreadMXBean();
        long[] ids = threads.getAllThreadIds();
        Map<Long, Long> currentCpu = sampleThreadCpuNanos(threads, ids);
        boolean sampleReady = previousThreadSampleAt != null;
        double elapsedSeconds = threadSampleElapsedSeconds();

        Map<String, ThreadGroupAccumulator> groups = new LinkedHashMap<>();
        List<Map<String, Object>> rankedThreads = new ArrayList<>();
        for (long id : ids) {
            ThreadInfo info = threads.getThreadInfo(id);
            if (info == null) {
                continue;
            }
            double cpuDelta = threadCpuPercentDelta(currentCpu, id, elapsedSeconds);
            String prefix = threadPrefix(info.getThreadName());
            ThreadGroupAccumulator group = groups.computeIfAbsent(prefix, ignored -> new ThreadGroupAccumulator(prefix));
            group.threadCount++;
            group.cpuPercentDelta += cpuDelta;
            if (cpuDelta > 0.1) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("name", info.getThreadName());
                row.put("cpuPercentDelta", round1(cpuDelta));
                rankedThreads.add(row);
            }
        }

        previousThreadCpuNanos.clear();
        previousThreadCpuNanos.putAll(currentCpu);
        previousThreadSampleAt = Instant.now();

        double attributedCpu = groups.values().stream()
                .mapToDouble(group -> group.cpuPercentDelta)
                .sum();

        List<Map<String, Object>> threadGroups = groups.values().stream()
                .sorted(Comparator.comparingDouble((ThreadGroupAccumulator group) -> group.cpuPercentDelta).reversed())
                .limit(12)
                .map(group -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("prefix", group.prefix);
                    row.put("threadCount", group.threadCount);
                    row.put("cpuPercentDelta", round1(group.cpuPercentDelta));
                    return row;
                })
                .toList();

        rankedThreads.sort(Comparator.comparingDouble(row -> -((Number) row.get("cpuPercentDelta")).doubleValue()));
        List<Map<String, Object>> topThreads = rankedThreads.stream().limit(8).toList();

        Map<String, Object> threadCpu = new LinkedHashMap<>();
        threadCpu.put("threadGroups", threadGroups);
        threadCpu.put("topThreads", topThreads);
        threadCpu.put("threadCpuAttributedPercent", round1(attributedCpu));
        threadCpu.put("threadSampleWindowSeconds", round1(elapsedSeconds));
        threadCpu.put("threadSampleReady", sampleReady);
        return threadCpu;
    }

    private Map<Long, Long> sampleThreadCpuNanos(ThreadMXBean threads, long[] ids) {
        Map<Long, Long> current = new LinkedHashMap<>();
        for (long id : ids) {
            if (threads.isThreadCpuTimeSupported()) {
                current.put(id, Math.max(0L, threads.getThreadCpuTime(id)));
            }
        }
        return current;
    }

    private double threadSampleElapsedSeconds() {
        Instant previous = previousThreadSampleAt;
        if (previous == null) {
            return 1.0;
        }
        return Math.max(0.25, Duration.between(previous, Instant.now()).toMillis() / 1000.0);
    }

    private double threadCpuPercentDelta(Map<Long, Long> currentCpu, long threadId, double elapsedSeconds) {
        Long current = currentCpu.get(threadId);
        if (current == null) {
            return 0.0;
        }
        Long previous = previousThreadCpuNanos.get(threadId);
        if (previous == null) {
            return 0.0;
        }
        long deltaNanos = Math.max(0L, current - previous);
        int processors = Runtime.getRuntime().availableProcessors();
        return (deltaNanos / 1_000_000_000.0) / elapsedSeconds / Math.max(1, processors) * 100.0;
    }

    private static String threadPrefix(String threadName) {
        if (threadName == null || threadName.isBlank()) {
            return "unknown";
        }
        if (threadName.startsWith("driver-ingress")) {
            return "driver-ingress";
        }
        if (threadName.startsWith("telemetry-ingress")) {
            return "telemetry-ingress";
        }
        if (threadName.startsWith("runtime-telemetry")) {
            return "runtime-telemetry";
        }
        if (threadName.startsWith("binding-")) {
            return "binding";
        }
        if (threadName.startsWith("ispf-driver-io")) {
            return "ispf-driver-io";
        }
        if (threadName.startsWith("ispf-driver-runtime")) {
            return "ispf-driver-runtime";
        }
        if (threadName.contains("object-change")) {
            return "object-change";
        }
        if (threadName.startsWith("http-nio")) {
            return "http-nio";
        }
        if (threadName.startsWith("G1")) {
            return "G1-gc";
        }
        if (threadName.startsWith("mqtt")) {
            return "mqtt";
        }
        int dash = threadName.indexOf('-');
        if (dash > 0) {
            return threadName.substring(0, dash);
        }
        return threadName;
    }

    private List<Map<String, Object>> buildSuspects(
            double processCpuPercent,
            Double heapUsedPercent,
            Map<String, Object> automation,
            Map<String, Object> database,
            Map<String, Object> drivers,
            Map<String, Object> detail
    ) {
        List<Map<String, Object>> suspects = new ArrayList<>();

        if (processCpuPercent >= CPU_HIGH) {
            suspects.add(suspect("subsystem", "critical", "high_cpu",
                    "Высокая загрузка CPU этой JVM", "processCpuPercent=" + round1(processCpuPercent), null, 90));
        }

        int objectChangeQueueSize = intValue(automation.get("objectChangeQueueSize"));
        if (objectChangeQueueSize >= OBJECT_CHANGE_QUEUE_WARN) {
            suspects.add(suspect("subsystem", "critical", "object_change_backlog",
                    "Перегруз object-change pipeline",
                    "queueSize=" + objectChangeQueueSize, null, 80));
        }

        long dropped = longValue(automation.get("objectChangeDroppedTotal"));
        if (dropped > 0) {
            suspects.add(suspect("subsystem", "critical", "object_change_dropped",
                    "Потери в object-change (queue full)", "dropped=" + dropped, null, 85));
        }

        int eventJournalQueueSize = intValue(automation.get("eventJournalQueueSize"));
        if (eventJournalQueueSize >= EVENT_JOURNAL_QUEUE_WARN) {
            suspects.add(suspect("subsystem", "warning", "event_journal_backlog",
                    "Event journal writer не успевает", "queueSize=" + eventJournalQueueSize, null, 70));
        }

        int variableHistoryQueueSize = intValue(automation.get("variableHistoryQueueSize"));
        if (variableHistoryQueueSize >= VARIABLE_HISTORY_QUEUE_WARN) {
            suspects.add(suspect("subsystem", "warning", "variable_history_backlog",
                    "Historian async backlog", "queueSize=" + variableHistoryQueueSize, null, 65));
        }

        int threadsAwaiting = intValue(database.get("threadsAwaitingConnection"));
        if (threadsAwaiting > 0) {
            suspects.add(suspect("subsystem", "critical", "jdbc_pool_exhausted",
                    "Исчерпан пул JDBC", "awaiting=" + threadsAwaiting, null, 75));
        }

        if (heapUsedPercent != null && heapUsedPercent >= 85.0) {
            suspects.add(suspect("subsystem", "warning", "heap_pressure",
                    "Давление на heap (возможен GC)", "heapUsedPercent=" + round1(heapUsedPercent), null, 60));
        }

        int ioPollQueueSize = intValue(detail.get("ioPollQueueSize"));
        if (ioPollQueueSize >= IO_POLL_QUEUE_WARN) {
            suspects.add(suspect("subsystem", "warning", "io_poll_backlog",
                    "Очередь driver poll I/O переполнена", "ioPollQueueSize=" + ioPollQueueSize, null, 72));
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> driverRows = (List<Map<String, Object>>) detail.get("drivers");
        if (driverRows != null && !driverRows.isEmpty()) {
            Map<String, Object> topDriver = driverRows.getFirst();
            int pressureScore = intValue(topDriver.get("pressureScore"));
            if (pressureScore >= DRIVER_PRESSURE_SUSPECT_MIN) {
                String devicePath = String.valueOf(topDriver.get("devicePath"));
                String driverId = String.valueOf(topDriver.get("driverId"));
                int pointCount = intValue(topDriver.get("pointMappingCount"));
                int pollIntervalMs = intValue(topDriver.get("pollIntervalMs"));
                String detailLine = "driverId=" + driverId
                        + ", pressureScore=" + pressureScore
                        + ", points=" + pointCount
                        + ", pollIntervalMs=" + pollIntervalMs;
                suspects.add(suspect(
                        "driver",
                        pressureScore >= 100 ? "critical" : "warning",
                        "driver_binding",
                        "Привязка драйвера " + devicePath,
                        detailLine,
                        devicePath,
                        Math.min(88, 30 + pressureScore)
                ));
            }
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> threadGroups = (List<Map<String, Object>>) detail.get("threadGroups");
        if (threadGroups != null && !threadGroups.isEmpty()) {
            Map<String, Object> topGroup = threadGroups.getFirst();
            double cpuDelta = ((Number) topGroup.getOrDefault("cpuPercentDelta", 0)).doubleValue();
            if (cpuDelta >= 5.0) {
                String prefix = String.valueOf(topGroup.get("prefix"));
                suspects.add(suspect("thread", "warning", "thread_group",
                        "Потоки " + prefix, "cpuPercentDelta=" + round1(cpuDelta), prefix, 68));
            }
            double pipelineCpu = pipelineThreadCpuPercent(threadGroups);
            if (pipelineCpu >= 8.0) {
                suspects.add(suspect("subsystem", "warning", "telemetry_pipeline",
                        "Поток telemetry / binding / MQTT (не poll драйвера)",
                        "pipelineCpuPercent=" + round1(pipelineCpu), null, 72));
            }
        }

        double attributedCpu = doubleValue(detail.get("threadCpuAttributedPercent"));
        boolean sampleReady = Boolean.TRUE.equals(detail.get("threadSampleReady"));
        if (sampleReady && processCpuPercent >= CPU_HIGH && attributedCpu > 0
                && processCpuPercent - attributedCpu >= 25.0) {
            suspects.add(suspect("subsystem", "info", "cpu_unattributed_threads",
                    "Часть CPU не разложена по JVM-потокам",
                    "processCpuPercent=" + round1(processCpuPercent)
                            + ", threadCpuAttributedPercent=" + round1(attributedCpu)
                            + " (GC/JIT/native или короткий интервал сэмпла)",
                    null, 40));
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> runningJobs = (List<Map<String, Object>>) detail.get("runningJobs");
        if (runningJobs != null) {
            for (Map<String, Object> job : runningJobs) {
                long runningSeconds = longValue(job.get("runningSeconds"));
                if (runningSeconds >= LONG_JOB_SECONDS) {
                    String jobId = String.valueOf(job.get("jobId"));
                    suspects.add(suspect("job", "warning", "long_running_job",
                            "Долгий platform job " + job.get("jobType"),
                            "runningSeconds=" + runningSeconds, jobId, 55));
                }
            }
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> runningWorkflows = (List<Map<String, Object>>) detail.get("runningWorkflows");
        if (runningWorkflows != null) {
            for (Map<String, Object> workflow : runningWorkflows) {
                long runningSeconds = longValue(workflow.get("runningSeconds"));
                if (runningSeconds >= LONG_WORKFLOW_SECONDS) {
                    String workflowPath = String.valueOf(workflow.get("workflowPath"));
                    suspects.add(suspect("workflow", "warning", "long_running_workflow",
                            "Долгий workflow " + workflowPath,
                            "runningSeconds=" + runningSeconds, workflowPath, 50));
                }
            }
        }

        long activeDrivers = longValue(drivers.get("activeDrivers"));
        if (activeDrivers > 0 && clusterProperties.hasCapability(com.ispf.server.config.ReplicaCapability.DRIVERS)) {
            String nodeLabel = clusterProperties.enabled() ? "на io-ноде" : "на этой ноде";
            suspects.add(suspect("subsystem", "info", "drivers_active",
                    "Активные драйверы " + nodeLabel, "activeDrivers=" + activeDrivers, null, 20));
        }

        if (processCpuPercent >= CPU_HIGH) {
            boolean hasStrongSuspect = suspects.stream()
                    .anyMatch(row -> intValue(row.get("score")) >= 70
                            && !"high_cpu".equals(row.get("id")));
            if (!hasStrongSuspect) {
                suspects.add(suspect("subsystem", "info", "cpu_unattributed",
                        "Высокая загрузка CPU без явного виновника в pipeline",
                        "processCpuPercent=" + round1(processCpuPercent)
                                + "; проверьте host (docker stats), демо-объекты и фоновые сервисы",
                        null, 42));
            }
        }

        suspects.sort(Comparator.comparingInt(row -> -intValue(row.get("score"))));
        return suspects;
    }

    private static Map<String, Object> suspect(
            String kind,
            String severity,
            String id,
            String title,
            String detail,
            String ref,
            int score
    ) {
        Map<String, Object> suspect = new LinkedHashMap<>();
        suspect.put("kind", kind);
        suspect.put("severity", severity);
        suspect.put("id", id);
        suspect.put("title", title);
        suspect.put("detail", detail);
        suspect.put("ref", ref != null ? ref : "");
        suspect.put("score", score);
        return suspect;
    }

    private static int pressureScore(double processCpuPercent, Map<String, Object> automation, Map<String, Object> drivers) {
        int score = (int) Math.round(processCpuPercent);
        score += Math.min(50, intValue(automation.get("objectChangeQueueSize")) / 10);
        score += Math.min(30, intValue(automation.get("eventJournalQueueSize")) / 5);
        score += Math.min(20, (int) longValue(drivers.get("activeDrivers")) * 2);
        return score;
    }

    private double processCpuPercent() {
        java.lang.management.OperatingSystemMXBean bean = ManagementFactory.getOperatingSystemMXBean();
        if (bean instanceof com.sun.management.OperatingSystemMXBean os) {
            double load = os.getProcessCpuLoad();
            if (load >= 0) {
                return load * 100.0;
            }
        }
        return 0.0;
    }

    private double systemCpuPercent() {
        java.lang.management.OperatingSystemMXBean bean = ManagementFactory.getOperatingSystemMXBean();
        if (bean instanceof com.sun.management.OperatingSystemMXBean os) {
            double load = os.getCpuLoad();
            if (load >= 0) {
                return load * 100.0;
            }
        }
        return -1.0;
    }

    private static Double heapUsedPercent(Map<String, Object> runtime) {
        long heapUsed = longValue(runtime.get("heapUsedBytes"));
        long heapMax = longValue(runtime.get("heapMaxBytes"));
        if (heapMax <= 0) {
            return null;
        }
        return heapUsed * 100.0 / heapMax;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    private static double pipelineThreadCpuPercent(List<Map<String, Object>> threadGroups) {
        double total = 0.0;
        for (Map<String, Object> group : threadGroups) {
            String prefix = String.valueOf(group.get("prefix"));
            if (isTelemetryPipelinePrefix(prefix)) {
                total += ((Number) group.getOrDefault("cpuPercentDelta", 0)).doubleValue();
            }
        }
        return total;
    }

    private static boolean isTelemetryPipelinePrefix(String prefix) {
        return switch (prefix) {
            case "telemetry", "telemetry-ingress", "runtime-telemetry", "binding", "mqtt",
                    "object-change", "variable", "event", "driver-ingress" -> true;
            default -> false;
        };
    }

    private static double doubleValue(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return 0.0;
    }

    private static int intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return 0;
    }

    private static long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return 0L;
    }

    private static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    public int jdbcThreadsAwaitingConnection() {
        if (dataSource instanceof HikariDataSource hikari) {
            HikariPoolMXBean pool = hikari.getHikariPoolMXBean();
            return pool.getThreadsAwaitingConnection();
        }
        return 0;
    }

    private static final class ThreadGroupAccumulator {
        private final String prefix;
        private int threadCount;
        private double cpuPercentDelta;

        private ThreadGroupAccumulator(String prefix) {
            this.prefix = prefix;
        }
    }
}
