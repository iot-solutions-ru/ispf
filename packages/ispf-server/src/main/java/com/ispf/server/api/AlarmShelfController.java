package com.ispf.server.api;

import com.ispf.server.alert.AlarmShelf;
import com.ispf.server.alert.AlarmShelfApprovalService;
import com.ispf.server.alert.AlarmShelfService;
import com.ispf.server.config.AlarmShelfProperties;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/v1/alarm-shelves")
public class AlarmShelfController {

    private final AlarmShelfService alarmShelfService;
    private final AlarmShelfApprovalService approvalService;
    private final AlarmShelfProperties alarmShelfProperties;

    public AlarmShelfController(
            AlarmShelfService alarmShelfService,
            AlarmShelfApprovalService approvalService,
            AlarmShelfProperties alarmShelfProperties
    ) {
        this.alarmShelfService = alarmShelfService;
        this.approvalService = approvalService;
        this.alarmShelfProperties = alarmShelfProperties;
    }

    @GetMapping
    public List<AlarmShelf> listActive() {
        return alarmShelfService.listActive();
    }

    @PostMapping
    public Object shelve(@RequestBody AlarmShelfService.ShelveAlarmRequest request, Authentication authentication) {
        if (alarmShelfProperties.isApprovalRequired()) {
            String requestedBy = authentication != null ? authentication.getName() : "system";
            return approvalService.submit(request, requestedBy);
        }
        return alarmShelfService.shelve(request);
    }

    @GetMapping("/requests")
    public List<AlarmShelfApprovalService.PendingShelfRequest> listPendingRequests() {
        return approvalService.listPending();
    }

    @PostMapping("/requests/{id}/approve")
    public AlarmShelf approveRequest(@PathVariable String id) {
        AlarmShelfApprovalService.PendingShelfRequest pending = approvalService.findPending(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown shelf request: " + id));
        approvalService.removePending(id);
        return alarmShelfService.shelve(pending.toShelveRequest());
    }

    @PostMapping("/requests/{id}/reject")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void rejectRequest(@PathVariable String id) {
        approvalService.reject(id);
    }

    @DeleteMapping("/{id}")
    public void unshelve(@PathVariable String id) {
        alarmShelfService.unshelve(id);
    }
}
