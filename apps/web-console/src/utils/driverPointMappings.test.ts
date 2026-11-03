import { describe, expect, it } from "vitest";
import {
  normalizeDriverPointMappingsJson,
  parseDriverPointMappings,
  parseDriverWriteValue,
} from "./driverPointMappings";

describe("parseDriverPointMappings", () => {
  it("parses variable to point mapping object", () => {
    expect(parseDriverPointMappings('{"temperature":"HOLDING:1:40001","status":"COIL:1:0"}')).toEqual({
      temperature: "HOLDING:1:40001",
      status: "COIL:1:0",
    });
  });

  it("returns empty object for invalid json", () => {
    expect(parseDriverPointMappings("{bad")).toEqual({});
    expect(parseDriverPointMappings("")).toEqual({});
  });

  it("extracts protocol address from extended haystack mapping objects", () => {
    expect(
      parseDriverPointMappings(
        JSON.stringify({
          sineWave: {
            point: "sim",
            haystackTags: ["point", "sensor", "temp"],
            unit: "°C",
            dis: "Sine wave",
          },
          presentValue: {
            address: "analog-value:1:present-value",
            tags: ["point", "cur"],
          },
          status: "sim",
        }),
      ),
    ).toEqual({
      sineWave: "sim",
      presentValue: "analog-value:1:present-value",
      status: "sim",
    });
  });
});

describe("normalizeDriverPointMappingsJson", () => {
  it("preserves extended mapping objects for persistence", () => {
    const raw = JSON.stringify(
      {
        voltage: {
          point: "ns=2;s=Voltage",
          haystackTags: ["point", "sensor", "elec"],
        },
      },
      null,
      2,
    );
    const normalized = normalizeDriverPointMappingsJson(raw);
    expect(JSON.parse(normalized)).toEqual({
      voltage: {
        point: "ns=2;s=Voltage",
        haystackTags: ["point", "sensor", "elec"],
      },
    });
  });
});

describe("parseDriverWriteValue", () => {
  it("coerces booleans and numbers", () => {
    expect(parseDriverWriteValue("true")).toEqual({ value: true });
    expect(parseDriverWriteValue("false")).toEqual({ value: false });
    expect(parseDriverWriteValue("42")).toEqual({ value: 42 });
    expect(parseDriverWriteValue("3.5")).toEqual({ value: 3.5 });
  });

  it("keeps strings as text", () => {
    expect(parseDriverWriteValue("on")).toEqual({ value: "on" });
  });
});
