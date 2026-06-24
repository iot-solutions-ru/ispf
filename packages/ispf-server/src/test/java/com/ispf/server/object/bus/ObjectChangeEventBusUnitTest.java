package com.ispf.server.object.bus;

import com.ispf.server.config.ObjectChangeProperties;
import com.ispf.server.object.ObjectChangeEvent;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class ObjectChangeEventBusUnitTest {

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
                Optional.empty()
        );
        bus.start();
        bus.submit(ObjectChangeEvent.variableUpdated("root.device", "temperature"));

        assertThat(trace).containsExactly("history", "alerts");
    }

    @Test
    void coalescesTelemetryUpdatesBeforeDispatch() throws InterruptedException {
        CopyOnWriteArrayList<String> trace = new CopyOnWriteArrayList<>();
        ObjectChangeProperties properties = new ObjectChangeProperties();
        properties.setAsyncEnabled(true);
        properties.setWorkerThreads(1);
        properties.setQueueCapacity(100);
        properties.setCoalesceTelemetryUpdates(true);

        ObjectChangeEventBus bus = new ObjectChangeEventBus(
                properties,
                List.of(event -> trace.add(event.path() + ":" + event.variableName())),
                Optional.empty()
        );
        bus.start();

        bus.submit(ObjectChangeEvent.variableUpdated("root.device", "temperature", true));
        bus.submit(ObjectChangeEvent.variableUpdated("root.device", "temperature", true));
        bus.submit(ObjectChangeEvent.variableUpdated("root.device", "temperature", true));

        TimeUnit.MILLISECONDS.sleep(200);

        assertThat(trace).hasSize(1);
        assertThat(trace.getFirst()).isEqualTo("root.device:temperature");
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
}
