package com.ispf.server.eventfilter;

import com.ispf.expression.ExpressionException;
import com.ispf.server.eventfilter.EventFilterObjectService.EventFilterDefinition;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.plugin.blueprint.SystemObjectStructureService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EventFilterObjectServiceInvalidExpressionTest {

    @Mock
    private ObjectManager objectManager;

    @Mock
    private SystemObjectStructureService structureService;

    @Mock
    private EventFilterMatcher eventFilterMatcher;

    @Test
    void unreadableExpressionIsRejectedBeforeSave() {
        doThrow(new ExpressionException("Invalid expression: 1 +"))
                .when(eventFilterMatcher)
                .validateExpression("1 +");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service().upsert(definition("1 +"))
        );

        assertEquals("filterExpression: Invalid expression: 1 +", exception.getMessage());
        verify(eventFilterMatcher).validateExpression("1 +");
        verify(objectManager, never()).create(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(objectManager, never()).setVariableValue(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    private EventFilterObjectService service() {
        return new EventFilterObjectService(objectManager, structureService, eventFilterMatcher);
    }

    private static EventFilterDefinition definition(String filterExpression) {
        return new EventFilterDefinition(
                "",
                "expr-proof",
                "Expr",
                "",
                "*",
                "root.platform.**",
                0L,
                100L,
                0L,
                filterExpression,
                false
        );
    }
}
