package com.ispf.server.function;

import com.ispf.core.binding.BindingRule;
import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.PlatformObject;
import com.ispf.server.config.MqttGatewayProperties;
import com.ispf.server.driver.DeviceTelemetryPolicyService;
import com.ispf.server.object.BindingRulesService;
import com.ispf.server.object.ObjectManager;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parallel MQTT ingress routing: one coalesced lane per topic or payload, dispatched on a thread pool
 * (bypasses single-slot {@code lastIngress} binding bottleneck).
 */
@Service
public class MqttGatewayIngressDispatchService {

    private static final Logger log = LoggerFactory.getLogger(MqttGatewayIngressDispatchService.class);

    private static final DataSchema INGRESS_SCHEMA = DataSchema.builder("mqttIngress")
            .field("topic", FieldType.STRING)
            .field("raw", FieldType.STRING)
            .build();

    private static final Pattern CALL_FUNCTION_LAST_INGRESS = Pattern.compile(
            "callFunction\\s*\\(\\s*([A-Za-z_][A-Za-z0-9_]*)\\s*,\\s*lastIngress\\s*\\)",
            Pattern.CASE_INSENSITIVE
    );

    private final MqttGatewayProperties properties;
    private final MqttGatewayFunctionHandler gatewayFunctionHandler;
    private final FunctionService functionService;
    private final ObjectManager objectManager;
    private final BindingRulesService bindingRulesService;
    private final DeviceTelemetryPolicyService policyService;
    private final ExecutorService executor;

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
        AtomicInteger threadIndex = new AtomicInteger();
        ThreadFactory threadFactory = runnable -> {
            Thread thread = new Thread(runnable, "mqtt-gateway-ingress-" + threadIndex.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        this.executor = Executors.newFixedThreadPool(properties.getIngressDispatchThreads(), threadFactory);
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
        executor.submit(() -> dispatchIngress(gatewayPath, resolvedFunction, resolvedTopic, resolvedRaw));
        return true;
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
            Matcher matcher = CALL_FUNCTION_LAST_INGRESS.matcher(rule.expression().trim());
            if (!matcher.matches()) {
                continue;
            }
            String functionName = matcher.group(1);
            if (node.functions().containsKey(functionName)) {
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

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }
}
