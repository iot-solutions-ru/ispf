import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

JAVA_GLOBS = [
    ROOT / "packages/ispf-server/src/main/java",
    ROOT / "packages/ispf-server/src/test/java",
    ROOT / "packages/ispf-expression/src/test/java",
    ROOT / "packages/ispf-plugin-model/src/test/java",
]

BINDING_RULE_REPLACEMENTS = [
    ('new ModelBindingRule(\n                                "sumIntFloat",\n                                "self.intValue.value + self.floatValue.value"\n                        )',
     'ModelBindingRule.of("sum-int-float", "sumIntFloat", "self.intValue.value + self.floatValue.value")'),
    ('new ModelBindingRule(\n                                "tableIntSum",\n                                "sumRecordField(table, int)"\n                        )',
     'ModelBindingRule.of("table-int-sum", "tableIntSum", "sumRecordField(table, int)")'),
    ('new ModelBindingRule(\n                                "tableIntSum",\n                                "sumRecordField(eventLog, int)"\n                        )',
     'ModelBindingRule.of("table-int-sum", "tableIntSum", "sumRecordField(eventLog, int)")'),
    ('new ModelBindingRule(\n                        "sumWaves",\n                        "self.sineWave.value + self.sawtoothWave.value"\n                )',
     'ModelBindingRule.of("sum-waves", "sumWaves", "self.sineWave.value + self.sawtoothWave.value")'),
    ('new ModelBindingRule(\n                        "alarmActive",\n                        "hysteresis(temperature, 35, 33)"\n                )',
     'ModelBindingRule.of("alarm-active", "alarmActive", "hysteresis(temperature, 35, 33)")'),
    ('new ModelBindingRule(\n                        "alarmActive",\n                        "self.temperature.value > self.threshold.value"\n                )',
     'ModelBindingRule.of("alarm-active", "alarmActive", "self.temperature.value > self.threshold.value")'),
]

for base in JAVA_GLOBS:
    for path in base.rglob("*.java"):
        text = path.read_text(encoding="utf-8")
        original = text
        text = text.replace("ModelBindingDefinition", "ModelBindingRule")
        text = re.sub(r",\s*null,\s*(?=DataRecord|Map\.of|List\.of|Instant\.now|new )", ", ", text)
        for old, new in BINDING_RULE_REPLACEMENTS:
            text = text.replace(old, new)
        text = re.sub(
            r'new ModelBindingRule\(\s*"([^"]+)",\s*"((?:[^"\\]|\\.)*)"\s*\)',
            lambda m: f'ModelBindingRule.of("{m.group(1)}", "{m.group(1)}", "{m.group(2)}")',
            text,
        )
        if text != original:
            path.write_text(text, encoding="utf-8")
            print("updated", path.relative_to(ROOT))
