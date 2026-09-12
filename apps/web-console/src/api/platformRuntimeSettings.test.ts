import { describe, expect, it } from "vitest";
import { isSensitiveUnchanged, type PlatformRuntimeSetting } from "./platformRuntimeSettings";

function setting(overrides: Partial<PlatformRuntimeSetting>): PlatformRuntimeSetting {
  return {
    id: "ai.api-key",
    envVar: "ISPF_AI_API_KEY",
    propertyKey: "ispf.ai.api-key",
    type: "string",
    value: "********",
    defaultValue: "",
    source: "file",
    sensitive: true,
    editable: true,
    hotReloadable: false,
    restartRequired: true,
    ...overrides,
  };
}

describe("isSensitiveUnchanged", () => {
  it("skips the masked placeholder so save does not overwrite the real key", () => {
    expect(isSensitiveUnchanged(setting({}), undefined)).toBe(true);
    expect(isSensitiveUnchanged(setting({}), "********")).toBe(true);
  });

  it("treats a newly typed secret as a change", () => {
    expect(isSensitiveUnchanged(setting({}), "sk-new")).toBe(false);
  });

  it("does not apply to non-sensitive fields", () => {
    expect(isSensitiveUnchanged(setting({ sensitive: false, value: "openai-compatible" }), "openai-compatible")).toBe(false);
  });
});
