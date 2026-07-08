package com.ispf.server.api;

import com.ispf.server.agent.AgentStoreForwardStats;
import com.ispf.server.agent.AgentStoreForwardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/agent/store-forward")
public class AgentStoreForwardController {

    private final AgentStoreForwardService storeForwardService;

    public AgentStoreForwardController(AgentStoreForwardService storeForwardService) {
        this.storeForwardService = storeForwardService;
    }

    @GetMapping("/stats")
    public AgentStoreForwardStats stats() {
        return storeForwardService.aggregateStats();
    }
}
