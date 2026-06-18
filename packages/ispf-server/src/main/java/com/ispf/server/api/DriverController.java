package com.ispf.server.api;

import com.ispf.driver.DriverMetadata;
import com.ispf.server.driver.DriverCatalog;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/drivers")
public class DriverController {

    private final DriverCatalog driverCatalog;

    public DriverController(DriverCatalog driverCatalog) {
        this.driverCatalog = driverCatalog;
    }

    @GetMapping
    public List<DriverMetadata> listDrivers() {
        return driverCatalog.list();
    }
}
