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
    @SuppressWarnings("ConditionalExpressionNumericPromotion") // the promotion IS the subject of this test
    void numericTernaryZeroPromotesToDoubleButWholeZeroStoresAsInteger() {
        // Java ternary promotes int 0 with double 0.0 to Double. INTEGER used to reject any Double
        // and skipped self-diagnostics bootstrap on demostand; whole 0.0 is now accepted.
        assertThatCode(() -> DataRecord.single(
                INTEGER_VALUE,
                Map.of("value", false ? 0.0 : 0)
        )).doesNotThrowAnyException();
        assertThat(DataRecord.single(
                INTEGER_VALUE,
                Map.of("value", false ? 0.0 : 0)
        ).firstRow().get("value")).isInstanceOf(Integer.class).isEqualTo(0);

        assertThatCode(() -> DataRecord.single(
                INTEGER_VALUE,
                Map.of("value", false ? Double.valueOf(0.0) : Integer.valueOf(0))
        )).doesNotThrowAnyException();

        assertThatThrownBy(() -> DataRecord.single(
                INTEGER_VALUE,
                Map.of("value", false ? 1.5 : 0.5)
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
