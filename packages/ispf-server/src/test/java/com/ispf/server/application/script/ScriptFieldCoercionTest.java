package com.ispf.server.application.script;

import com.ispf.core.model.FieldDefinition;
import com.ispf.core.model.FieldType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

  @Test
  void rejectsFractionalIntegerInsteadOfTruncating() {
    FieldDefinition pages = FieldDefinition.required("pages", FieldType.INTEGER);
    IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> ScriptFieldCoercion.coerce(pages, 1.9)
    );
    assertEquals(true, ex.getMessage().contains("must be integer"));
  }

  @Test
  void rejectsOutOfRangeInteger() {
    FieldDefinition pages = FieldDefinition.required("pages", FieldType.INTEGER);
    IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> ScriptFieldCoercion.coerce(pages, Integer.MAX_VALUE + 1L)
    );
    assertEquals(true, ex.getMessage().contains("out of integer range"));
  }
}
