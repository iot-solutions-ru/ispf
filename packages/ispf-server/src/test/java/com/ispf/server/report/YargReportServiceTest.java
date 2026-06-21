package com.ispf.server.report;

import com.haulmont.yarg.formatters.factory.DefaultFormatterFactory;
import com.haulmont.yarg.loaders.factory.DefaultLoaderFactory;
import com.haulmont.yarg.loaders.impl.JsonDataLoader;
import com.haulmont.yarg.reporting.Reporting;
import com.haulmont.yarg.reporting.RunParams;
import com.haulmont.yarg.structure.ReportOutputType;
import com.haulmont.yarg.structure.impl.BandBuilder;
import com.haulmont.yarg.structure.impl.ReportBuilder;
import com.haulmont.yarg.structure.impl.ReportTemplateBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class YargReportServiceTest {

    @Test
    void rendersXlsxFromJsonBandTemplate() throws Exception {
        byte[] template = ReportYargTemplateTestHelper.smokeTestTemplate();
        String jsonData = """
                {"rows":[{"col1":"A1","col2":"ready"}]}
                """;

        var reportTemplate = new ReportTemplateBuilder()
                .code("DEFAULT")
                .documentName("report.xls")
                .documentPath("report.xls")
                .documentContent(template)
                .outputType(ReportOutputType.xls)
                .outputNamePattern("report.xls")
                .build();

        var report = new ReportBuilder()
                .name("test")
                .band(new BandBuilder()
                        .name("Band1")
                        .query("", "parameter=reportData $.rows", "json")
                        .build())
                .template(reportTemplate)
                .build();

        Reporting reporting = new Reporting();
        reporting.setFormatterFactory(new DefaultFormatterFactory());
        reporting.setLoaderFactory(new DefaultLoaderFactory().setJsonDataLoader(new JsonDataLoader()));

        var document = reporting.runReport(
                new RunParams(report)
                        .templateCode("DEFAULT")
                        .output(ReportOutputType.xls)
                        .param("reportData", jsonData)
        );

        assertTrue(document.getContent().length > 0, "YARG document should not be empty");
    }
}
