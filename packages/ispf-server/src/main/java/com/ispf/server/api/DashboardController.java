package com.ispf.server.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ispf.server.dashboard.DashboardService;
import com.ispf.server.federation.FederationProxyService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/dashboards")
public class DashboardController {

    private final DashboardService dashboardService;
    private final FederationProxyService federationProxyService;
    private final ObjectMapper objectMapper;

    public DashboardController(
            DashboardService dashboardService,
            FederationProxyService federationProxyService,
            ObjectMapper objectMapper
    ) {
        this.dashboardService = dashboardService;
        this.federationProxyService = federationProxyService;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/by-path")
    public DashboardService.DashboardView get(@RequestParam String path) {
        return federationProxyService.resolve(path)
                .map(target -> objectMapper.convertValue(
                        federationProxyService.proxyDashboard(target),
                        DashboardService.DashboardView.class
                ))
                .orElseGet(() -> dashboardService.getDashboard(path));
    }

    @PutMapping("/by-path/layout")
    public DashboardService.DashboardView saveLayout(
            @RequestParam String path,
            @RequestBody SaveLayoutRequest request
    ) {
        requireLocalDashboard(path);
        return dashboardService.saveLayout(path, request.layoutJson());
    }

    @PutMapping("/by-path/title")
    public DashboardService.DashboardView saveTitle(
            @RequestParam String path,
            @RequestBody SaveTitleRequest request
    ) {
        requireLocalDashboard(path);
        return dashboardService.updateTitle(path, request.title());
    }

    private void requireLocalDashboard(String path) {
        if (federationProxyService.resolve(path).isPresent()) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Federated dashboards are read-only on this instance"
            );
        }
    }

    public record SaveLayoutRequest(@NotBlank String layoutJson) {
    }

    public record SaveTitleRequest(@NotBlank String title) {
    }
}
