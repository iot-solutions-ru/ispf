package com.ispf.server.application.script;

import com.ispf.core.model.FieldDefinition;
import com.ispf.core.model.FieldType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ScriptFieldCoercionTest {

  @Test
  void coercesLongLiteralToIntegerField() {
    FieldDefinition pages = FieldDefinition.required("pages", FieldType.INTEGER);
    assertEquals(0, ScriptFieldCoercion.coerce(pages, 0L));
    assertEquals(10, ScriptFieldCoercion.coerce(pages, 10L));
  }

  @Test
  void fillsIntegerDefaultsForMissingOutputFields() {
    FieldDefinition pages = FieldDefinition.required("pages", FieldType.INTEGER);
    assertEquals(0, ScriptFieldCoercion.coerce(pages, null));
  }
}
