import { describe, expect, it } from "vitest";
import { parseDriverConfigJson } from "./driverDefaults";

describe("parseDriverConfigJson", () => {
  it("keeps string values and stringifies nested JSON", () => {
    const parsed = parseDriverConfigJson(`{
      "host": "127.0.0.1",
      "port": 9090,
      "subscribeText": "{\\"op\\":\\"subscribe\\"}"
    }`);
    expect(parsed.host).toBe("127.0.0.1");
    expect(parsed.port).toBe("9090");
    expect(parsed.subscribeText).toBe('{"op":"subscribe"}');
  });

  it("rejects text that is not an object", () => {
    expect(() => parseDriverConfigJson("[]")).toThrow("invalid");
    expect(() => parseDriverConfigJson("{")).toThrow("invalid");
  });
});
