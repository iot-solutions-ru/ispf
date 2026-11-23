package com.ispf.server.driver;

import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.server.spi.DeviceObjectAccess;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DriverOwnershipSchedulerTest {

    private static final String DEVICE = "root.platform.devices.reclaim-01";

    private final DriverOwnershipService ownership = mock(DriverOwnershipService.class);
    private final DriverRuntimeService runtime = mock(DriverRuntimeService.class);
    private final DeviceObjectAccess objects = mock(DeviceObjectAccess.class);
    private final DriverOwnershipScheduler scheduler = new DriverOwnershipScheduler(ownership, runtime, objects);

    @BeforeEach
    void expiredLeaseOnADevice() {
        when(ownership.isEnabled()).thenReturn(true);
        when(ownership.findExpiredDevicePaths()).thenReturn(List.of(DEVICE));
        when(ownership.tryAcquire(DEVICE)).thenReturn(true);
        when(runtime.isActiveLocally(DEVICE)).thenReturn(false);
        when(objects.require(DEVICE)).thenReturn(
                new PlatformObject("d1", DEVICE, ObjectType.DEVICE, "Reclaim", "", null));
    }

    @Test
    void reloadsADeviceWhoseBindingOnlyTheDatabaseHas() {
        when(runtime.readBinding(DEVICE)).thenReturn(Optional.empty(), Optional.of(DriverBinding.virtualDemo()));

        scheduler.reclaimExpiredDriverLocks();

        InOrder order = inOrder(objects, ownership, runtime);
        order.verify(objects).reloadFromDatabase(DEVICE);
        order.verify(ownership).tryAcquire(DEVICE);
        order.verify(runtime).startHeld(DEVICE);
    }

    @Test
    void reclaimsWithoutAReloadWhenTheBindingIsInMemory() {
        when(runtime.readBinding(DEVICE)).thenReturn(Optional.of(DriverBinding.virtualDemo()));

        scheduler.reclaimExpiredDriverLocks();

        verify(objects, never()).reloadFromDatabase(DEVICE);
        verify(runtime).startHeld(DEVICE);
    }

    @Test
    void leavesADeviceWithoutABindingAlone() {
        when(runtime.readBinding(DEVICE)).thenReturn(Optional.empty());

        scheduler.reclaimExpiredDriverLocks();

        verify(objects).reloadFromDatabase(DEVICE);
        verify(ownership, never()).tryAcquire(DEVICE);
        verify(runtime, never()).startHeld(DEVICE);
    }
}
