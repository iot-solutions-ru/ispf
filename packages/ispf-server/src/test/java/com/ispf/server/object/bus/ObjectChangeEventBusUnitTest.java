package com.ispf.server.object.bus;

import com.ispf.server.config.ObjectChangeProperties;
import com.ispf.server.object.ObjectChangeEvent;
import com.ispf.server.platform.AutomationMetricsRecorder;
import com.ispf.server.platform.AutomationObservationSupport;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class ObjectChangeEventBusUnitTest {

    private static final AutomationMetricsRecorder METRICS = new AutomationMetricsRecorder(Optional.empty());
    private static final AutomationObservationSupport OBSERVATION =
            new AutomationObservationSupport(Optional.empty());

    @Test
    void dispatchesHandlersInOrderWhenSync() {
        CopyOnWriteArrayList<String> trace = new CopyOnWriteArrayList<>();
        ObjectChangeProperties properties = new ObjectChangeProperties();
        properties.setAsyncEnabled(false);

        ObjectChangeEventBus bus = new ObjectChangeEventBus(
                properties,
                List.of(
                        handler(trace, 20, "alerts"),
                        handler(trace, 10, "history")
                ),
                METRICS,
                OBSERVATION
        );
        bus.start();
        bus.submit(ObjectChangeEvent.variableUpdated("root.device", "temperature"));

        assertThat(trace).containsExactly("history", "alerts");
    }

    @Test
    void coalescesTelemetryUpdatesBeforeDispatchInUnifiedMode() throws InterruptedException {
        CopyOnWriteArrayList<String> trace = new CopyOnWriteArrayList<>();
        ObjectChangeProperties properties = new ObjectChangeProperties();
        properties.setAsyncEnabled(true);
        properties.setSplitLanesEnabled(false);
        properties.setWorkerThreads(1);
        properties.setQueueCapacity(100);
        properties.setCoalesceTelemetryUpdates(true);

        ObjectChangeEventBus bus = new ObjectChangeEventBus(
                properties,
                List.of(event -> trace.add(event.path() + ":" + event.variableName())),
                METRICS,
                OBSERVATION
        );
        bus.start();

        bus.submit(ObjectChangeEvent.variableUpdated("root.device", "temperature", true));
        bus.submit(ObjectChangeEvent.variableUpdated("root.device", "temperature", true));
        bus.submit(ObjectChangeEvent.variableUpdated("root.device", "temperature", true));

        TimeUnit.MILLISECONDS.sleep(200);

        assertThat(trace).hasSize(1);
        assertThat(trace.getFirst()).isEqualTo("root.device:temperature");
    }

    @Test
    void coalescesTelemetryUpdatesOnTelemetryLaneWhenSplit() throws InterruptedException {
        CopyOnWriteArrayList<String> telemetryTrace = new CopyOnWriteArrayList<>();
        ObjectChangeProperties properties = new ObjectChangeProperties();
        properties.setAsyncEnabled(true);
        properties.setSplitLanesEnabled(true);
        properties.setTelemetryWorkerThreads(1);
        properties.setAutomationWorkerThreads(1);
        properties.setCoalesceTelemetryUpdates(true);

        ObjectChangeEventBus bus = new ObjectChangeEventBus(
                properties,
                List.of(
                        telemetryHandler(telemetryTrace, "history"),
                        handler(new CopyOnWriteArrayList<>(), 20, "alerts")
                ),
                METRICS,
                OBSERVATION
        );
        bus.start();

        bus.submit(ObjectChangeEvent.variableUpdated("root.device", "temperature", true));
        bus.submit(ObjectChangeEvent.variableUpdated("root.device", "temperature", true));
        bus.submit(ObjectChangeEvent.variableUpdated("root.device", "temperature", true));

        TimeUnit.MILLISECONDS.sleep(200);

        assertThat(telemetryTrace).hasSize(1);
        assertThat(telemetryTrace.getFirst()).isEqualTo("root.device:temperature");
    }

    @Test
    void isolatesTelemetryAndAutomationLanes() throws InterruptedException {
        CopyOnWriteArrayList<String> automationTrace = new CopyOnWriteArrayList<>();
        CountDownLatch telemetryStarted = new CountDownLatch(1);
        CountDownLatch automationProcessed = new CountDownLatch(1);
        CountDownLatch releaseTelemetry = new CountDownLatch(1);

        ObjectChangeProperties properties = new ObjectChangeProperties();
        properties.setAsyncEnabled(true);
        properties.setSplitLanesEnabled(true);
        properties.setTelemetryWorkerThreads(1);
        properties.setAutomationWorkerThreads(1);
        properties.setCoalesceTelemetryUpdates(false);

        ObjectChangeEventBus bus = new ObjectChangeEventBus(
                properties,
                List.of(
                        new ObjectChangeAsyncHandler() {
                            @Override
                            public ObjectChangeLane lane() {
                                return ObjectChangeLane.TELEMETRY;
                            }

                            @Override
                            public void handle(ObjectChangeEvent event) {
                                telemetryStarted.countDown();
                                try {
                                    releaseTelemetry.await(5, TimeUnit.SECONDS);
                                } catch (InterruptedException ex) {
                                    Thread.currentThread().interrupt();
                                }
                            }
                        },
                        event -> {
                            automationTrace.add(event.path() + ":" + event.variableName());
                            automationProcessed.countDown();
                        }
                ),
                METRICS,
                OBSERVATION
        );
        bus.start();

        bus.submit(ObjectChangeEvent.variableUpdated("root.device", "temperature", true));

        assertThat(telemetryStarted.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(automationProcessed.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(automationTrace).containsExactly("root.device:temperature");

        bus.submit(ObjectChangeEvent.variableUpdated("root.device", "pressure", false));
        TimeUnit.MILLISECONDS.sleep(100);
        assertThat(automationTrace).containsExactly(
                "root.device:temperature",
                "root.device:pressure"
        );

        releaseTelemetry.countDown();
    }

    @Test
    void skipsAutomationLaneForTelemetryOnlyDriverUpdates() throws InterruptedException {
        CopyOnWriteArrayList<String> automationTrace = new CopyOnWriteArrayList<>();
        ObjectChangeProperties properties = new ObjectChangeProperties();
        properties.setAsyncEnabled(true);
        properties.setSplitLanesEnabled(true);
        properties.setTelemetryWorkerThreads(1);
        properties.setAutomationWorkerThreads(1);
        properties.setCoalesceTelemetryUpdates(false);

        ObjectChangeEventBus bus = new ObjectChangeEventBus(
                properties,
                List.of(
                        telemetryHandler(new CopyOnWriteArrayList<>(), "history"),
                        event -> automationTrace.add(event.path() + ":" + event.variableName())
                ),
                METRICS,
                OBSERVATION
        );
        bus.start();

        bus.submit(ObjectChangeEvent.variableUpdated("root.device", "temperature", true, false));
        TimeUnit.MILLISECONDS.sleep(200);

        assertThat(automationTrace).isEmpty();
    }

    private static ObjectChangeAsyncHandler handler(CopyOnWriteArrayList<String> trace, int order, String name) {
        return new ObjectChangeAsyncHandler() {
            @Override
            public int order() {
                return order;
            }

            @Override
            public void handle(ObjectChangeEvent event) {
                trace.add(name);
            }
        };
    }

    private static ObjectChangeAsyncHandler telemetryHandler(CopyOnWriteArrayList<String> trace, String name) {
        return new ObjectChangeAsyncHandler() {
            @Override
            public ObjectChangeLane lane() {
                return ObjectChangeLane.TELEMETRY;
            }

            @Override
            public void handle(ObjectChangeEvent event) {
                trace.add(event.path() + ":" + event.variableName());
            }
        };
    }
}
