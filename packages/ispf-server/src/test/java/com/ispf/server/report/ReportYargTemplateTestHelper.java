package com.ispf.server.report;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

final class ReportYargTemplateTestHelper {

    private ReportYargTemplateTestHelper() {
    }

    static byte[] smokeTestTemplate() {
        try (InputStream input = ReportYargTemplateTestHelper.class.getResourceAsStream("/yarg/smoke-test.xls")) {
            if (input == null) {
                throw new IllegalStateException("Missing test resource /yarg/smoke-test.xls");
            }
            return input.readAllBytes();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
