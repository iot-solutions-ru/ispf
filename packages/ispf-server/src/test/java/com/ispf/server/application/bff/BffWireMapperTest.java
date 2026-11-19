package com.ispf.server.application.bff;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BffWireMapperTest {

    @Test
    void unknownWireProfileIsRejected() {
        DataRecord output = DataRecord.single(
                DataSchema.builder("out").field("ok", FieldType.BOOLEAN).build(),
                Map.of("ok", true)
        );

        assertThatThrownBy(() -> BffWireMapper.toWire(output, "NO_SUCH_PROFILE", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported wireProfile");
    }

    @Test
    void operatorWireProfileStampsWireProfile() {
        DataRecord output = DataRecord.single(
                DataSchema.builder("out").field("ok", FieldType.BOOLEAN).build(),
                Map.of("ok", true)
        );

        Map<String, Object> wire = BffWireMapper.toWire(output, IspfOperatorWireProfile.ID, null);

        assertThat(wire).containsEntry("wireProfile", IspfOperatorWireProfile.ID);
        assertThat(wire).containsEntry("error_code", "OK");
    }

    @Test
    void blankWireProfileUsesDefaultWireWithoutStamp() {
        DataRecord output = DataRecord.single(
                DataSchema.builder("out").field("ok", FieldType.BOOLEAN).build(),
                Map.of("ok", true)
        );

        Map<String, Object> wire = BffWireMapper.toWire(output, null, null);

        assertThat(wire).containsEntry("error_code", "OK");
        assertThat(wire).doesNotContainKey("wireProfile");
        assertThat(wire).containsKey("result");
    }
}
