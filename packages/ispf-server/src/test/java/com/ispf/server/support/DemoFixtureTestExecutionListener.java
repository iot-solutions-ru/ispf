package com.ispf.server.support;

import com.ispf.server.plugin.blueprint.BlueprintApplicationRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Profiles;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.support.AbstractTestExecutionListener;

/** Restores demo fixture objects when a prior test deleted them from the shared Spring context. */
public final class DemoFixtureTestExecutionListener extends AbstractTestExecutionListener {

    @Override
    public void beforeTestMethod(TestContext testContext) {
        ApplicationContext context = testContext.getApplicationContext();
        if (context == null) {
            return;
        }
        if (!context.getEnvironment().acceptsProfiles(Profiles.of("test"))) {
            return;
        }
        if (!context.getEnvironment().getProperty("ispf.bootstrap.fixtures-enabled", Boolean.class, true)) {
            return;
        }
        if (!context.containsBean("BlueprintApplicationRunner")) {
            return;
        }
        context.getBean(BlueprintApplicationRunner.class).ensureTestFixtures();
    }
}
