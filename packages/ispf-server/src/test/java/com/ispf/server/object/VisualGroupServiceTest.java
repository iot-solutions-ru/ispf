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

    @Test
    void removesDeletedMemberFromVisualGroups() {
        String groupPath = "root.platform.devices.test-visual-group-cleanup";
        String memberPath = "root.platform.devices.demo-sensor-01";

        if (objectManager.tree().findByPath(groupPath).isEmpty()) {
            objectManager.create(
                    "root.platform.devices",
                    "test-visual-group-cleanup",
                    ObjectType.VISUAL_GROUP,
                    "Cleanup Test Group",
                    "",
                    null
            );
        }

        visualGroupService.setMembers(groupPath, java.util.List.of());
        visualGroupService.addMembers(groupPath, java.util.List.of(memberPath));
        assertThat(visualGroupService.listMembers(groupPath))
                .extracting(VisualGroupMember::path)
                .containsExactly(memberPath);

        objectManager.delete(memberPath);

        assertThat(objectManager.tree().findByPath(memberPath)).isEmpty();
        assertThat(visualGroupService.listMembers(groupPath)).isEmpty();
    }

    @Test
    void hidesMembersFromStructuralTreeWhileAllowingMultipleGroups() {
        String groupA = "root.platform.devices.test-visual-group-a";
        String groupB = "root.platform.devices.test-visual-group-b";
        String memberPath = "root.platform.devices.demo-sensor-01";

        for (String groupPath : java.util.List.of(groupA, groupB)) {
            if (objectManager.tree().findByPath(groupPath).isEmpty()) {
                objectManager.create(
                        "root.platform.devices",
                        groupPath.substring(groupPath.lastIndexOf('.') + 1),
                        ObjectType.VISUAL_GROUP,
                        "Group " + groupPath,
                        "",
                        null
                );
            }
            visualGroupService.setMembers(groupPath, java.util.List.of());
        }

        assertThat(visualGroupService.isHiddenFromStructuralTree(memberPath)).isFalse();

        visualGroupService.addMembers(groupA, java.util.List.of(memberPath));
        assertThat(visualGroupService.isHiddenFromStructuralTree(memberPath)).isTrue();
        assertThat(visualGroupService.listMembers(groupA))
                .extracting(VisualGroupMember::path)
                .containsExactly(memberPath);

        visualGroupService.addMembers(groupB, java.util.List.of(memberPath));
        assertThat(visualGroupService.isHiddenFromStructuralTree(memberPath)).isTrue();
        assertThat(visualGroupService.listMembers(groupB))
                .extracting(VisualGroupMember::path)
                .containsExactly(memberPath);

        visualGroupService.removeMembers(groupA, java.util.List.of(memberPath));
        assertThat(visualGroupService.isHiddenFromStructuralTree(memberPath)).isTrue();

        visualGroupService.removeMembers(groupB, java.util.List.of(memberPath));
        assertThat(visualGroupService.isHiddenFromStructuralTree(memberPath)).isFalse();

        objectManager.delete(groupA);
        objectManager.delete(groupB);
    }
}
