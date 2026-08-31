package com.ispf.server.ai.agent;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class AgentSessionRepositoryTransactionalTest {

    @Test
    void saveTurnIsTransactional() throws Exception {
        Method saveTurn = AgentSessionRepository.class.getDeclaredMethod(
                "saveTurn",
                AgentSession.class,
                AgentTurn.class
        );
        assertThat(saveTurn.getAnnotation(Transactional.class)).isNotNull();
    }
}
