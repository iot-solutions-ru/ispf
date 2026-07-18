package com.ispf.server.mimic;

import com.ispf.core.object.ObjectType;
import com.ispf.server.object.ObjectManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class MimicServiceEnsureExistsTest {

    @Autowired
    MimicService mimicService;

    @Autowired
    ObjectManager objectManager;

    @Test
    void ensureMimicExists_createsEmptyCanvasWhenMissing() {
        String path = "root.platform.mimics.facility-overview-ensure-test";
        objectManager.tree().findByPath(path).ifPresent(node -> objectManager.delete(path));

        MimicService.MimicView created = mimicService.ensureMimicExists(path, "Facility ensure test");
        assertEquals(path, created.path());
        assertTrue(created.diagramJson().contains("\"elements\""));
        assertEquals(ObjectType.MIMIC, objectManager.require(path).type());

        MimicService.MimicView again = mimicService.ensureMimicExists(path, "Facility ensure test");
        assertEquals(created.diagramJson(), again.diagramJson());
    }
}
