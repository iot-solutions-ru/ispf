package com.ispf.server.object;

import com.ispf.core.object.ObjectType;
import com.ispf.core.object.VisualGroupMember;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class VisualGroupServiceTest {

    @Autowired
    private ObjectManager objectManager;

    @Autowired
    private VisualGroupService visualGroupService;

    @Test
    void addRemoveMembersAndCleanupOnDelete() {
        String groupPath = "root.platform.devices.test-visual-group";
        String memberPath = "root.platform.devices.demo-sensor-01";

        if (objectManager.tree().findByPath(groupPath).isEmpty()) {
            objectManager.create(
                    "root.platform.devices",
                    "test-visual-group",
                    ObjectType.VISUAL_GROUP,
                    "Test Visual Group",
                    "",
                    null
            );
        }

        visualGroupService.setMembers(groupPath, java.util.List.of());
        assertThat(visualGroupService.listMembers(groupPath)).isEmpty();

        visualGroupService.addMembers(groupPath, java.util.List.of(memberPath));
        assertThat(visualGroupService.listMembers(groupPath))
                .extracting(VisualGroupMember::path)
                .containsExactly(memberPath);

        visualGroupService.removeMembers(groupPath, java.util.List.of(memberPath));
        assertThat(visualGroupService.listMembers(groupPath)).isEmpty();

        visualGroupService.addMembers(groupPath, java.util.List.of(memberPath));
        assertThatThrownBy(() -> visualGroupService.addMembers(groupPath, java.util.List.of(groupPath)))
                .isInstanceOf(IllegalArgumentException.class);

        objectManager.delete(groupPath);
        assertThat(objectManager.tree().findByPath(groupPath)).isEmpty();
    }
}
