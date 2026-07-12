package com.ispf.server.query;

import com.ispf.core.ref.PlatformRef;
import com.ispf.server.ref.PlatformRefExecutor;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class ObjectQueryPatchServiceTest {

    @Test
    void rejectsInvalidPatchJson() {
        ObjectQueryPatchService service = new ObjectQueryPatchService(
                new ObjectMapper(),
                Mockito.mock(PlatformRefExecutor.class)
        );
        assertThatThrownBy(() -> service.apply("not-json", "root.platform"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void appliesArrayPatch() {
        PlatformRefExecutor executor = Mockito.mock(PlatformRefExecutor.class);
        when(executor.write(any(PlatformRef.class), eq(42), eq("root.platform.queries.test"))).thenReturn(true);
        ObjectQueryPatchService service = new ObjectQueryPatchService(new ObjectMapper(), executor);
        ObjectQueryPatchService.PatchResult result = service.apply(
                "[{\"ref\":\"root.platform.devices.a/setpoint\",\"value\":42}]",
                "root.platform.queries.test"
        );
        assertThat(result.applied()).isEqualTo(1);
        assertThat(result.failed()).isZero();
    }
}
