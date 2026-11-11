import { describe, expect, it } from "vitest";
import { OperatorAppRequestError, operatorAppSaveNeedsCreate } from "./operatorApps";

describe("operatorAppSaveNeedsCreate", () => {
  it("creates the app only after a 404 save", () => {
    expect(operatorAppSaveNeedsCreate(new OperatorAppRequestError(404, "missing"))).toBe(true);
    expect(operatorAppSaveNeedsCreate(new OperatorAppRequestError(400, "externalSpaUrl must be an http(s) URL"))).toBe(
      false
    );
    expect(operatorAppSaveNeedsCreate(new Error("Operator app already exists"))).toBe(false);
  });
});
