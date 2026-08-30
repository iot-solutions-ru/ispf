package com.ispf.server.platform;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlatformSelfDiagnosticsBootstrapZeroValueTest {

    private static final DataSchema INTEGER_VALUE = DataSchema.builder("integerValue")
            .field("value", FieldType.INTEGER)
            .build();
    private static final DataSchema DOUBLE_VALUE = DataSchema.builder("doubleValue")
            .field("value", FieldType.DOUBLE)
            .build();

    @Test
    void numericTernaryZeroPromotesToDoubleAndBreaksIntegerSchema() {
        // Documents the Java trap that skipped self-diagnostics bootstrap on demostand.
        assertThatThrownBy(() -> DataRecord.single(
                INTEGER_VALUE,
                Map.of("value", false ? 0.0 : 0)
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be integer");

        // Even boxed Double/Integer still promote via unboxing in a numeric conditional.
        assertThatThrownBy(() -> DataRecord.single(
                INTEGER_VALUE,
                Map.of("value", false ? Double.valueOf(0.0) : Integer.valueOf(0))
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be integer");
    }

    @Test
    void ifElseIntegerZeroAccepted() {
        Object zeroValue;
        if (false) {
            zeroValue = 0.0;
        } else {
            zeroValue = 0;
        }
        assertThatCode(() -> DataRecord.single(INTEGER_VALUE, Map.of("value", zeroValue)))
                .doesNotThrowAnyException();
        DataRecord record = DataRecord.single(INTEGER_VALUE, Map.of("value", zeroValue));
        assertThat(record.firstRow().get("value")).isInstanceOf(Integer.class).isEqualTo(0);
    }

    @Test
    void ifElseDoubleZeroAccepted() {
        Object zeroValue;
        if (true) {
            zeroValue = 0.0;
        } else {
            zeroValue = 0;
        }
        DataRecord record = DataRecord.single(DOUBLE_VALUE, Map.of("value", zeroValue));
        assertThat(record.firstRow().get("value")).isInstanceOf(Double.class).isEqualTo(0.0);
    }
}
