package com.ispf.server.operator;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/operator-apps")
public class OperatorAppController {

    private final OperatorAppUiService service;
    private final OperatorStarterTemplatesService starterTemplatesService;

    public OperatorAppController(
            OperatorAppUiService service,
            OperatorStarterTemplatesService starterTemplatesService
    ) {
        this.service = service;
        this.starterTemplatesService = starterTemplatesService;
    }

    @GetMapping
    public List<Map<String, Object>> listApps(Authentication authentication) {
        return service.listApps(authentication);
    }

    @GetMapping("/starters")
    public List<Map<String, Object>> listStarters(Authentication authentication) {
        if (service.isTenantScoped(authentication)) {
            return List.of();
        }
        return starterTemplatesService.listStarters();
    }

    @PostMapping("/starters/install")
    public Map<String, Object> installStarters(Authentication authentication) {
        try {
            if (service.isTenantScoped(authentication)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Global operator starters are not available to tenants");
            }
            return starterTemplatesService.installStarters(false);
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage(), ex);
        }
    }

    @GetMapping("/{appId}/ui")
    public Map<String, Object> getUi(@PathVariable String appId, Authentication authentication) {
        try {
            return service.getUi(appId, authentication);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage(), ex);
        }
    }

    @PostMapping("/{appId}")
    public Map<String, Object> createApp(
            @PathVariable String appId,
            @RequestBody CreateOperatorAppRequest request
    ) {
        try {
            return service.createApp(appId, request.title());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage(), ex);
        }
    }

    @PutMapping("/{appId}/ui")
    public Map<String, Object> saveUi(@PathVariable String appId, @RequestBody SaveOperatorUiRequest request) {
        try {
            return service.saveUi(
                    appId,
                    request.title(),
                    request.defaultDashboard(),
                    request.dashboards(),
                    request.alarmBar(),
                    request.agentInstructions(),
                    request.hideTasksAndEvents(),
                    request.hideDashboardNav()
            );
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage(), ex);
        }
    }

    public record CreateOperatorAppRequest(String title) {
    }

    public record SaveOperatorUiRequest(
            String title,
            String defaultDashboard,
            List<Map<String, String>> dashboards,
            Map<String, Object> alarmBar,
            String agentInstructions,
            Boolean hideTasksAndEvents,
            Boolean hideDashboardNav
    ) {
    }
}
