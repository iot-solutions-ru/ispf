package com.ispf.server.driver.pack;

import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverMetadata;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.springframework.stereotype.Service;

@Service
public class LicensedDriverRegistry {

    private final Map<String, LicensedDriverBinding> bindingsByDriverId = new ConcurrentHashMap<>();

    public void register(LicensedDriverBinding binding) {
        bindingsByDriverId.put(binding.driverId(), binding);
    }

    public boolean contains(String driverId) {
        return bindingsByDriverId.containsKey(driverId);
    }

    public Optional<LicensedDriverBinding> find(String driverId) {
        return Optional.ofNullable(bindingsByDriverId.get(driverId));
    }

    public DeviceDriver create(String driverId) {
        LicensedDriverBinding binding = bindingsByDriverId.get(driverId);
        if (binding == null) {
            throw new IllegalArgumentException("Unknown licensed driver: " + driverId);
        }
        return binding.driverSupplier().get();
    }

    public List<DriverMetadata> metadata() {
        return bindingsByDriverId.values().stream()
                .map(LicensedDriverBinding::metadata)
                .toList();
    }

    public record LicensedDriverBinding(
            String packId,
            String driverId,
            DriverMetadata metadata,
            Supplier<DeviceDriver> driverSupplier
    ) {
    }
}
