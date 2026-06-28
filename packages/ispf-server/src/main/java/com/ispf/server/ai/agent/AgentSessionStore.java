package com.ispf.server.ai.agent;

import com.ispf.server.config.AiProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Service
public class AgentSessionStore {

    private final AgentSessionRepository repository;
    private final AiProperties aiProperties;

    public AgentSessionStore(AgentSessionRepository repository, AiProperties aiProperties) {
        this.repository = repository;
        this.aiProperties = aiProperties;
    }

    public AgentSession create(String actor, String rootPath) {
        evictExpired();
        AgentSession session = AgentSession.create(actor, rootPath);
        repository.insert(session);
        return session;
    }

    public AgentSession createOperator(String actor, OperatorAgentScope scope) {
        evictExpired();
        AgentSession session = AgentSession.create(actor, scope.briefingRoot());
        session.runState().setAgentProfile(AgentProfile.OPERATOR);
        session.runState().setOperatorAppId(scope.appId());
        repository.insert(session);
        return session;
    }

    public Optional<AgentSession> get(String sessionId, String actor) {
        evictExpired();
        return repository.find(sessionId, actor);
    }

    public Optional<AgentSession> require(String sessionId, String actor) {
        return get(sessionId, actor);
    }

    public boolean delete(String sessionId, String actor) {
        return repository.delete(sessionId, actor);
    }

    public void persistAfterTurn(AgentSession session, AgentTurn turn) {
        repository.saveTurn(session, turn);
    }

    public void persistState(AgentSession session) {
        repository.update(session);
    }

    @Scheduled(fixedDelayString = "${ispf.ai.agent-eviction-interval-ms:3600000}")
    public void evictExpired() {
        Duration ttl = Duration.ofHours(Math.max(1, aiProperties.getAgentSessionTtlHours()));
        Instant cutoff = Instant.now().minus(ttl);
        repository.deleteExpired(cutoff);
    }
}
