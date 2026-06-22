package com.ispf.server.ai.agent;

import com.ispf.server.config.AiProperties;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AgentSessionStore {

    private final Map<String, AgentSession> sessionsById = new ConcurrentHashMap<>();
    private final AiProperties aiProperties;

    public AgentSessionStore(AiProperties aiProperties) {
        this.aiProperties = aiProperties;
    }

    public AgentSession create(String actor, String rootPath) {
        evictExpired();
        AgentSession session = AgentSession.create(actor, rootPath);
        sessionsById.put(session.sessionId(), session);
        return session;
    }

    public Optional<AgentSession> get(String sessionId, String actor) {
        evictExpired();
        AgentSession session = sessionsById.get(sessionId);
        if (session == null) {
            return Optional.empty();
        }
        if (!session.actor().equals(actor)) {
            return Optional.empty();
        }
        return Optional.of(session);
    }

    public Optional<AgentSession> require(String sessionId, String actor) {
        return get(sessionId, actor);
    }

    public boolean delete(String sessionId, String actor) {
        AgentSession session = sessionsById.get(sessionId);
        if (session == null) {
            return false;
        }
        if (!session.actor().equals(actor)) {
            return false;
        }
        sessionsById.remove(sessionId);
        return true;
    }

    private void evictExpired() {
        Duration ttl = Duration.ofHours(Math.max(1, aiProperties.getAgentSessionTtlHours()));
        Instant cutoff = Instant.now().minus(ttl);
        sessionsById.entrySet().removeIf(entry -> entry.getValue().updatedAt().isBefore(cutoff));
    }
}
