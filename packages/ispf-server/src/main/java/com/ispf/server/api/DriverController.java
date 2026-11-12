package com.ispf.server.api;

import com.ispf.server.driver.DriverCatalog;
import com.ispf.server.driver.DriverListing;
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
    public List<DriverListing> listDrivers() {
        return driverCatalog.listLabeled();
    }
}
