package com.ispf.server.api;

import com.ispf.server.dashboard.DashboardService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboards")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/by-path")
    public DashboardService.DashboardView get(@RequestParam String path) {
        return dashboardService.getDashboard(path);
    }

    @PutMapping("/by-path/layout")
    public DashboardService.DashboardView saveLayout(
            @RequestParam String path,
            @RequestBody SaveLayoutRequest request
    ) {
        return dashboardService.saveLayout(path, request.layoutJson());
    }

    @PutMapping("/by-path/title")
    public DashboardService.DashboardView saveTitle(
            @RequestParam String path,
            @RequestBody SaveTitleRequest request
    ) {
        return dashboardService.updateTitle(path, request.title());
    }

    public record SaveLayoutRequest(@NotBlank String layoutJson) {
    }

    public record SaveTitleRequest(@NotBlank String title) {
    }
}
