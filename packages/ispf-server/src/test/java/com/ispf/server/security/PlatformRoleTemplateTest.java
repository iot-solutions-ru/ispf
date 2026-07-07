package com.ispf.server.security;

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

        assertThat(mesSupervisor.get("displayName")).isEqualTo("MES Supervisor");
        assertThat(String.valueOf(mesSupervisor.get("description"))).contains("MES supervisor");
        assertThat(mesSupervisor.get("objectPath"))
                .isEqualTo(PlatformUserService.ROLES_FOLDER + "." + PlatformRoleService.MES_SUPERVISOR);
    }
}
