package com.ispf.expression;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;

class ExpressionEngineCacheTest {

    @Test
    void compileReturnsCachedInstanceForSameExpression() {
        ExpressionEngine engine = new ExpressionEngine();
        ExpressionEngine.CompiledExpression first = engine.compile("true");
        ExpressionEngine.CompiledExpression second = engine.compile("true");
        assertSame(first, second);
    }
}
