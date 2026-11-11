import { describe, expect, it } from "vitest";
import { inputMapIssue, payloadJsonIssue } from "./bpmnIspfValue";

describe("bpmn inputMap", () => {
  it("accepts pairs and a scalar JSON object", () => {
    expect(inputMapIssue("")).toBeNull();
    expect(inputMapIssue("orderId=${workflow.orderId}")).toBeNull();
    expect(inputMapIssue('{"orderId":"${workflow.orderId}","n":1}')).toBeNull();
  });

  it("rejects a bare word and a nested JSON value", () => {
    expect(inputMapIssue("not-a-pair")).toBe("pairs");
    expect(inputMapIssue("a=1, =2")).toBe("pairs");
    expect(inputMapIssue('{"row":{"a":1}}')).toBe("shape");
    expect(inputMapIssue("{")).toBe("json");
  });
});

describe("bpmn payloadJson", () => {
  it("requires a scalar JSON object", () => {
    expect(payloadJsonIssue("")).toBeNull();
    expect(payloadJsonIssue('{"level":"high"}')).toBeNull();
    expect(payloadJsonIssue("[1]")).toBe("shape");
    expect(payloadJsonIssue("{")).toBe("json");
  });
});
