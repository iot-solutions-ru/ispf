package com.ispf.server.security;

import com.ispf.server.object.ObjectManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class PlatformRoleTemplateTest {

    @Autowired
    private PlatformRoleService roleService;

    @Autowired
    private ObjectManager objectManager;

    @Test
    void seedsOperatorReadonlyAndMesSupervisorTemplates() {
        roleService.ensureDefaultRoles();

        Map<String, Object> operatorReadonly = roleService.listRoles().stream()
                .filter(role -> PlatformRoleService.OPERATOR_READONLY.equals(role.get("name")))
                .findFirst()
                .orElseThrow();
        Map<String, Object> mesSupervisor = roleService.listRoles().stream()
                .filter(role -> PlatformRoleService.MES_SUPERVISOR.equals(role.get("name")))
                .findFirst()
                .orElseThrow();

        assertThat(operatorReadonly.get("displayName")).isEqualTo("Operator (read-only)");
        assertThat(String.valueOf(operatorReadonly.get("description"))).contains("Read-only operator");
        assertThat(operatorReadonly.get("builtIn")).isEqualTo(false);
        assertThat(operatorReadonly.get("template")).isEqualTo(true);

        assertThat(mesSupervisor.get("displayName")).isEqualTo("MES Supervisor");
        assertThat(String.valueOf(mesSupervisor.get("description"))).contains("MES supervisor");
        assertThat(mesSupervisor.get("objectPath"))
                .isEqualTo(PlatformUserService.ROLES_FOLDER + "." + PlatformRoleService.MES_SUPERVISOR);
        assertThat(mesSupervisor.get("template")).isEqualTo(true);

        String mesPath = PlatformUserService.ROLES_FOLDER + "." + PlatformRoleService.MES_SUPERVISOR;
        var mesNode = objectManager.require(mesPath);
        assertThat(mesNode.getVariable(PlatformRoleTemplatePermissions.OPERATOR_AGENT_TOOLS_VAR)).isPresent();
        assertThat(mesNode.getVariable(PlatformRoleTemplatePermissions.SCOPE_PATH_PREFIXES_VAR)).isPresent();
        var toolsRecord = mesNode.getVariable(PlatformRoleTemplatePermissions.OPERATOR_AGENT_TOOLS_VAR)
                .orElseThrow()
                .value()
                .orElseThrow();
        String toolsJson = String.valueOf(toolsRecord.get("value", 0));
        assertThat(toolsJson).contains("run_report");
        assertThat(toolsJson).contains("list_work_queue");
    }
}
