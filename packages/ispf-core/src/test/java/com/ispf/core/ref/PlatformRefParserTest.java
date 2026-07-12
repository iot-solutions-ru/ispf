package com.ispf.core.ref;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlatformRefParserTest {

    @ParameterizedTest
    @CsvSource({
            "@/temperature, @, temperature, value",
            "root.platform.devices.a/temperature, root.platform.devices.a, temperature, value",
            "root.platform.devices.demo-sensor-01/temperature, root.platform.devices.demo-sensor-01, temperature, value",
            "root.platform.devices.mini-tec-plant.gpu-01/activePowerKw, root.platform.devices.mini-tec-plant.gpu-01, activePowerKw, value",
            "@/temperature/quality, @, temperature, quality",
    })
    void parsesSlashVariableRefs(String input, String object, String name, String field) {
        PlatformRef ref = PlatformRefParser.parse(input);
        assertThat(ref.object()).isEqualTo(object);
        assertThat(ref.kind()).isEqualTo(PlatformRefKind.VARIABLE);
        assertThat(ref.name()).isEqualTo(name);
        assertThat(ref.field()).isEqualTo(field);
        assertThat(PlatformRefFormatter.format(ref)).isEqualTo(input.contains("/quality")
                ? input
                : object + "/" + name);
    }

    @Test
    void parsesFunctionEventAndTagRefs() {
        PlatformRef fn = PlatformRefParser.parse("@/fn/calculate");
        assertThat(fn.kind()).isEqualTo(PlatformRefKind.FUNCTION);
        assertThat(fn.name()).isEqualTo("calculate");

        PlatformRef evt = PlatformRefParser.parse("root.platform.devices.pump/evt/overload");
        assertThat(evt.kind()).isEqualTo(PlatformRefKind.EVENT);
        assertThat(evt.object()).isEqualTo("root.platform.devices.pump");

        PlatformRef tag = PlatformRefParser.parse("root.platform.devices.a/tag/rule-1");
        assertThat(tag.kind()).isEqualTo(PlatformRefKind.TAG);
        assertThat(tag.name()).isEqualTo("rule-1");
    }

    @Test
    void bareIdentIsCurrentObjectVariable() {
        PlatformRef ref = PlatformRefParser.parseVariableSource("temperature");
        assertThat(ref.isCurrentObject()).isTrue();
        assertThat(ref.name()).isEqualTo("temperature");
    }

    @Test
    void rejectsLegacyRefAtSyntax() {
        assertThatThrownBy(() -> PlatformRefParser.parse("refAt(\"root.platform.devices.remote\", temperature)"))
                .isInstanceOf(PlatformRefParseException.class);
    }

    @Test
    void rejectsHashTagPath() {
        assertThatThrownBy(() -> PlatformRefParser.parse("root.platform.devices.a#rule-1"))
                .isInstanceOf(PlatformRefParseException.class);
    }

    @Test
    void extractRefsFromReadExpression() {
        String expr = "read(\"root.platform.devices.dev-03/sineWave\") + read(\"root.platform.devices.dev-02/sineWave\")";
        var refs = PlatformRefParser.extractRefsFromExpression(expr);
        assertThat(refs).hasSize(2);
    }

    @Test
    void resolveCurrentObject() {
        PlatformRef ref = PlatformRefParser.parse("@/temperature");
        PlatformRef resolved = ref.resolveObject("root.platform.devices.local");
        assertThat(resolved.object()).isEqualTo("root.platform.devices.local");
    }

    @Test
    void parseHistorianSourceRequiresSlashRef() {
        PlatformRef ref = PlatformRefParser.parseHistorianSource(
                "root.platform.devices.analytics-demo.chain-a/derived-a",
                null
        );
        assertThat(ref.object()).isEqualTo("root.platform.devices.analytics-demo.chain-a");
        assertThat(ref.name()).isEqualTo("derived-a");
        assertThatThrownBy(() -> PlatformRefParser.parseHistorianSource(
                "root.platform.devices.analytics-demo.chain-a.derived-a",
                null
        )).isInstanceOf(PlatformRefParseException.class);
    }

    @Test
    void fromJsonFields() {
        PlatformRef ref = PlatformRefConfig.requireVariable(
                null,
                "root.platform.devices.a",
                "temperature",
                "value"
        );
        assertThat(ref.object()).isEqualTo("root.platform.devices.a");
        assertThat(ref.name()).isEqualTo("temperature");
    }
}
