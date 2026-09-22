import { useState } from "react";
import { describe, expect, it } from "vitest";
import userEvent from "@testing-library/user-event";
import { screen } from "@testing-library/react";
import DataSchemaEditor from "./DataSchemaEditor";
import { renderWithInspector } from "../../test/renderWithInspector";
import { scalarValueSchema } from "../../utils/schema/dataSchema";
import type { DataSchema } from "../../types";

function Harness({ initial }: { initial: DataSchema }) {
  const [schema, setSchema] = useState(initial);
  return <DataSchemaEditor value={schema} onChange={setSchema} idPrefix="test-schema" />;
}

describe("DataSchemaEditor", () => {
  it("keeps focus while a field name is typed", async () => {
    const user = userEvent.setup();
    renderWithInspector(
      <Harness initial={scalarValueSchema("reading", "DOUBLE")} />,
    );

    const fieldName = screen.getByDisplayValue("value");
    await user.click(fieldName);
    await user.keyboard("Reading");

    const updated = screen.getByDisplayValue("valueReading");
    expect(updated).toHaveFocus();
  });
});
