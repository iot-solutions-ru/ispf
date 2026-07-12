package com.ispf.server.function;

import com.ispf.core.binding.BindingRule;
import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.PlatformObject;
import com.ispf.server.concurrent.ElasticWorkerLauncher;
import com.ispf.server.config.MqttGatewayProperties;
import com.ispf.server.driver.DeviceTelemetryPolicyService;
import com.ispf.server.object.BindingRulesService;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.object.TelemetryIngressCoalesceKey;
import com.ispf.driver.ingress.ElasticWorkerScaler;
import com.ispf.driver.ingress.IngressElasticSettings;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parallel MQTT ingress routing on an elastic worker pool (bypasses single-slot {@code lastIngress}
 * binding bottleneck). Coalesce mode ({@link MqttGatewayProperties#isIngressDispatchCoalesceEnabled()})
 * last-value-wins per lane; FIFO mode queues every sample. When
 * {@link MqttGatewayProperties#isIngressBypassL3Queue()} is true, {@link ObjectManager} schedules here
 * before L3.
 */
@Service
public class MqttGatewayIngressDispatchService {

    private static final Logger log = LoggerFactory.getLogger(MqttGatewayIngressDispatchService.class);

    private static final DataSchema INGRESS_SCHEMA = DataSchema.builder("mqttIngress")
            .field("topic", FieldType.STRING)
            .field("raw", FieldType.STRING)
            .build();

    private static final Pattern CALL_DISPATCH = Pattern.compile(
            "call\\s*\\(\\s*@/fn/([A-Za-z_][A-Za-z0-9_]*)\\s*,\\s*@/lastIngress\\s*\\)",
            Pattern.CASE_INSENSITIVE
    );

    private final MqttGatewayProperties properties;
    private final MqttGatewayFunctionHandler gatewayFunctionHandler;
    private final FunctionService functionService;
    private final ObjectManager objectManager;
    private final BindingRulesService bindingRulesService;
    private final DeviceTelemetryPolicyService policyService;

    private final ConcurrentHashMap<String, DispatchTask> pendingByKey = new ConcurrentHashMap<>();
    private final AtomicInteger coalescePendingCount = new AtomicInteger();
    private LinkedBlockingQueue<DispatchTask> pendingFifo;
    private ElasticWorkerLauncher workers;
    private volatile boolean workersStarted;

    public MqttGatewayIngressDispatchService(
            MqttGatewayProperties properties,
            MqttGatewayFunctionHandler gatewayFunctionHandler,
            @Lazy FunctionService functionService,
            ObjectManager objectManager,
            BindingRulesService bindingRulesService,
            DeviceTelemetryPolicyService policyService
    ) {
        this.properties = properties;
        this.gatewayFunctionHandler = gatewayFunctionHandler;
        this.functionService = functionService;
        this.objectManager = objectManager;
        this.bindingRulesService = bindingRulesService;
        this.policyService = policyService;
    }

    public synchronized void ensureWorkersStarted() {
        if (workersStarted) {
            return;
        }
        pendingFifo = new LinkedBlockingQueue<>(properties.getIngressDispatchQueueCapacity());
        IngressElasticSettings elastic = properties.resolvedIngressDispatchElastic();
        workers = new ElasticWorkerLauncher(
                elastic,
                this::pendingQueueDepth,
                "mqtt-gateway-ingress",
                this::drainOneDispatch
        );
        workers.start();
        workersStarted = true;
        log.info(
                "MQTT gateway ingress dispatch started (capacity={}, coalesce={}, workers={}-{}, elastic={})",
                properties.getIngressDispatchQueueCapacity(),
                properties.isIngressDispatchCoalesceEnabled(),
                elastic.resolvedMinWorkers(),
                elastic.resolvedMaxWorkers(),
                elastic.enabled()
        );
    }

    public boolean isWorkersStarted() {
        return workersStarted;
    }

    /**
     * @return true when dispatch was scheduled (caller should skip binding/event path for this tick)
     */
    public boolean tryScheduleDispatch(String gatewayPath, String variableName, DataRecord value) {
        if (!"lastIngress".equals(variableName)) {
            return false;
        }
        if (!policyService.parallelIngressDispatch(gatewayPath)) {
            return false;
        }
        ensureWorkersStarted();
        Optional<String> functionName = resolveIngressFunction(gatewayPath);
        if (functionName.isEmpty()) {
            return false;
        }
        Optional<String> topic = ingressTopic(value);
        Optional<String> raw = ingressRaw(value);
        if (topic.isEmpty() || raw.isEmpty()) {
            return false;
        }
        String resolvedTopic = topic.get();
        String resolvedRaw = raw.get();
        String resolvedFunction = functionName.get();
        DispatchTask task = new DispatchTask(gatewayPath, resolvedFunction, resolvedTopic, resolvedRaw);
        if (properties.isIngressDispatchCoalesceEnabled()) {
            return scheduleCoalesced(task, gatewayPath, variableName, value, resolvedFunction, resolvedTopic, resolvedRaw);
        }
        return scheduleFifo(task, gatewayPath, resolvedFunction, resolvedTopic, resolvedRaw);
    }

    private boolean scheduleCoalesced(
            DispatchTask task,
            String gatewayPath,
            String variableName,
            DataRecord value,
            String resolvedFunction,
            String resolvedTopic,
            String resolvedRaw
    ) {
        String key = TelemetryIngressCoalesceKey.laneKey(
                gatewayPath,
                variableName,
                value,
                policyService.ingressPayloadLanes(gatewayPath)
        );
        DispatchTask previous = pendingByKey.put(key, task);
        if (previous == null) {
            int size = coalescePendingCount.incrementAndGet();
            if (size > properties.getIngressDispatchQueueCapacity()) {
                pendingByKey.remove(key, task);
                coalescePendingCount.decrementAndGet();
                dispatchIngress(gatewayPath, resolvedFunction, resolvedTopic, resolvedRaw);
                return true;
            }
        }
        workers.signalWork();
        return true;
    }

    private boolean scheduleFifo(
            DispatchTask task,
            String gatewayPath,
            String resolvedFunction,
            String resolvedTopic,
            String resolvedRaw
    ) {
        if (!pendingFifo.offer(task)) {
            dispatchIngress(gatewayPath, resolvedFunction, resolvedTopic, resolvedRaw);
            return true;
        }
        workers.signalWork();
        return true;
    }

    private int pendingQueueDepth() {
        return properties.isIngressDispatchCoalesceEnabled()
                ? coalescePendingCount.get()
                : pendingFifo.size();
    }

    private boolean drainOneDispatch() {
        if (properties.isIngressDispatchCoalesceEnabled()) {
            return drainOneCoalesced();
        }
        DispatchTask task = pendingFifo.poll();
        if (task == null) {
            return false;
        }
        dispatchIngress(task.gatewayPath(), task.functionName(), task.topic(), task.raw());
        return true;
    }

    private boolean drainOneCoalesced() {
        Iterator<Map.Entry<String, DispatchTask>> iterator = pendingByKey.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, DispatchTask> entry = iterator.next();
            DispatchTask task = pendingByKey.remove(entry.getKey());
            if (task != null) {
                coalescePendingCount.decrementAndGet();
                dispatchIngress(task.gatewayPath(), task.functionName(), task.topic(), task.raw());
                return true;
            }
        }
        return false;
    }

    private void dispatchIngress(String gatewayPath, String functionName, String topic, String raw) {
        try {
            if (MqttGatewayFunctionHandler.FUNCTION_NAME.equals(functionName)) {
                gatewayFunctionHandler.dispatchIngress(gatewayPath, topic, raw, true);
                return;
            }
            DataRecord ingress = DataRecord.single(INGRESS_SCHEMA, Map.of("topic", topic, "raw", raw));
            functionService.invoke(gatewayPath, functionName, ingress);
        } catch (Exception ex) {
            log.warn("Parallel ingress dispatch failed for {} function {} topic {}: {}",
                    gatewayPath, functionName, topic, ex.getMessage());
        }
    }

    private Optional<String> resolveIngressFunction(String path) {
        PlatformObject node = objectManager.tree().findByPath(path).orElse(null);
        if (node == null) {
            return Optional.empty();
        }
        if (node.functions().containsKey(MqttGatewayFunctionHandler.FUNCTION_NAME)) {
            return Optional.of(MqttGatewayFunctionHandler.FUNCTION_NAME);
        }
        for (BindingRule rule : bindingRulesService.listRules(path)) {
            if (!rule.enabled()) {
                continue;
            }
            Matcher matcher = CALL_DISPATCH.matcher(rule.expression().trim());
            if (!matcher.matches()) {
                continue;
            }
            String functionName = matcher.group(1);
            if (MqttGatewayFunctionHandler.FUNCTION_NAME.equals(functionName)
                    || node.functions().containsKey(functionName)) {
                return Optional.of(functionName);
            }
        }
        return Optional.empty();
    }

    public static Optional<String> ingressTopic(DataRecord value) {
        return ingressField(value, "topic");
    }

    public static Optional<String> ingressRaw(DataRecord value) {
        return ingressField(value, "raw");
    }

    public static boolean isIngressPayload(DataRecord value) {
        return ingressTopic(value).filter(topic -> !topic.isBlank()).isPresent()
                && ingressRaw(value).filter(raw -> !raw.isBlank()).isPresent();
    }

    private static Optional<String> ingressField(DataRecord value, String field) {
        if (value == null || value.rowCount() == 0) {
            return Optional.empty();
        }
        Object raw = value.firstRow().get(field);
        return raw == null ? Optional.empty() : Optional.of(raw.toString());
    }

    public void flushNow() {
        if (workersStarted) {
            drainAllRemaining();
        }
    }

    @PreDestroy
    void shutdown() {
        if (workers != null) {
            workers.close();
        }
        if (workersStarted) {
            drainAllRemaining();
        }
    }

    private void drainAllRemaining() {
        DispatchTask task;
        while ((task = pollOne()) != null) {
            dispatchIngress(task.gatewayPath(), task.functionName(), task.topic(), task.raw());
        }
    }

    private DispatchTask pollOne() {
        if (properties.isIngressDispatchCoalesceEnabled()) {
            Iterator<Map.Entry<String, DispatchTask>> iterator = pendingByKey.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, DispatchTask> entry = iterator.next();
                DispatchTask removed = pendingByKey.remove(entry.getKey());
                if (removed != null) {
                    coalescePendingCount.decrementAndGet();
                    return removed;
                }
            }
            return null;
        }
        return pendingFifo.poll();
    }

    private record DispatchTask(String gatewayPath, String functionName, String topic, String raw) {}
}
