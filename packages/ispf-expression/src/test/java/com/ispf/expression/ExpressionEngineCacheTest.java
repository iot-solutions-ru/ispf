package com.ispf.expression;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExpressionEngineCacheTest {

    @Test
    void compileReturnsCachedInstanceForSameExpression() {
        ExpressionEngine engine = new ExpressionEngine();
        ExpressionEngine.CompiledExpression first = engine.compile("true");
        ExpressionEngine.CompiledExpression second = engine.compile("true");
        assertSame(first, second);
    }

    @Test
    void compileDropsTheLeastRecentlyUsedExpressionPastTheCap() {
        ExpressionEngine engine = new ExpressionEngine(2);
        ExpressionEngine.CompiledExpression kept = engine.compile("true");
        engine.compile("false");
        assertSame(kept, engine.compile("true"));
        engine.compile("1 + 1");
        org.junit.jupiter.api.Assertions.assertEquals(2, engine.compiledCacheSize());
        assertSame(kept, engine.compile("true"));
    }

    @Test
    void invalidExpressionIsNotCached() {
        ExpressionEngine engine = new ExpressionEngine(2);
        assertThrows(ExpressionException.class, () -> engine.compile("???"));
        org.junit.jupiter.api.Assertions.assertEquals(0, engine.compiledCacheSize());
    }
}
