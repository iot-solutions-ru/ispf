package com.ispf.server.report;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.security.acl.VariableAclRequestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class TreeVariablesReportAclTest {

    @Autowired
    private ReportService reportService;

    @Autowired
    private ObjectManager objectManager;

    private String devicePath;
    private String reportPath;

    @AfterEach
    void cleanup() {
        if (reportPath != null && objectManager.tree().findByPath(reportPath).isPresent()) {
            objectManager.delete(reportPath);
        }
        if (devicePath != null && objectManager.tree().findByPath(devicePath).isPresent()) {
            objectManager.delete(devicePath);
        }
        reportPath = null;
        devicePath = null;
    }

    @Test
    @Transactional
    void memberRunOmitsRestrictedTreeVariableRows() {
        String suffix = String.valueOf(System.nanoTime());
        String deviceName = "tv-acl-" + suffix;
        devicePath = "root.platform.devices." + deviceName;
        objectManager.create(
                "root.platform.devices",
                deviceName,
                ObjectType.DEVICE,
                "Tree-variables ACL test",
                "",
                null
        );
        DataSchema schema = DataSchema.builder("secret")
                .field("value", FieldType.DOUBLE)
                .build();
        objectManager.createVariable(
                devicePath,
                "secret",
                schema,
                true,
                true,
                DataRecord.single(schema, Map.of("value", 8675309.0)),
                true,
                null,
                List.of("engineer"),
                List.of()
        );

        String reportId = "tv-acl-report-" + suffix;
        reportPath = ReportService.reportPath(reportId);
        reportService.deployTreeVariables(
                reportId,
                "Tree-variables ACL report",
                "",
                devicePath,
                "secret",
                List.of(
                        new ReportService.ReportColumn("devicepath", "Device path"),
                        new ReportService.ReportColumn("value", "Value")
                ),
                100
        );

        var operator = UsernamePasswordAuthenticationToken.authenticated(
                "operator",
                "n/a",
                List.of()
        );

        @SuppressWarnings("unchecked")
        Map<String, Object> memberResult = VariableAclRequestContext.callAsMember(
                operator,
                () -> reportService.run(reportPath, Map.of())
        );
        assertThat(memberResult.get("rowCount")).isEqualTo(0);
        assertThat((List<?>) memberResult.get("rows")).isEmpty();

        @SuppressWarnings("unchecked")
        Map<String, Object> systemResult = reportService.run(reportPath, Map.of());
        assertThat(systemResult.get("rowCount")).isEqualTo(1);
        List<Map<String, Object>> rows = (List<Map<String, Object>>) systemResult.get("rows");
        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row).containsEntry("devicepath", devicePath);
            assertThat(row).containsEntry("value", 8675309.0);
        });
    }

    @Test
    @Transactional
    void memberRunIncludesReadableTreeVariableRows() {
        String suffix = String.valueOf(System.nanoTime());
        String deviceName = "tv-acl-open-" + suffix;
        devicePath = "root.platform.devices." + deviceName;
        objectManager.create(
                "root.platform.devices",
                deviceName,
                ObjectType.DEVICE,
                "Tree-variables open ACL test",
                "",
                null
        );
        DataSchema schema = DataSchema.builder("status")
                .field("value", FieldType.STRING)
                .build();
        objectManager.createVariable(
                devicePath,
                "status",
                schema,
                true,
                true,
                DataRecord.single(schema, Map.of("value", "ok")),
                false,
                null,
                List.of(),
                List.of()
        );

        String reportId = "tv-acl-open-report-" + suffix;
        reportPath = ReportService.reportPath(reportId);
        reportService.deployTreeVariables(
                reportId,
                "Tree-variables open report",
                "",
                devicePath,
                "status",
                List.of(
                        new ReportService.ReportColumn("devicepath", "Device path"),
                        new ReportService.ReportColumn("value", "Value")
                ),
                100
        );

        var operator = UsernamePasswordAuthenticationToken.authenticated(
                "operator",
                "n/a",
                List.of()
        );

        @SuppressWarnings("unchecked")
        Map<String, Object> memberResult = VariableAclRequestContext.callAsMember(
                operator,
                () -> reportService.run(reportPath, Map.of())
        );
        assertThat(memberResult.get("rowCount")).isEqualTo(1);
        List<Map<String, Object>> rows = (List<Map<String, Object>>) memberResult.get("rows");
        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row).containsEntry("devicepath", devicePath);
            assertThat(row).containsEntry("value", "ok");
        });
    }
}
