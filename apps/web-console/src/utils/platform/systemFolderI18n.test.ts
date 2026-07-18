import i18n from "i18next";
import { beforeAll, describe, expect, it } from "vitest";
import enObjectTree from "../../locales/en/objectTree.json";
import { localizedSystemFolderMeta } from "./systemFolderI18n";

beforeAll(async () => {
  await i18n.init({
    lng: "en",
    fallbackLng: "en",
    returnEmptyString: false,
    resources: {
      en: {
        objectTree: enObjectTree,
      },
    },
  });
});

describe("localizedSystemFolderMeta", () => {
  const t = i18n.getFixedT("en");

  it("localizes known catalog folders", () => {
    const meta = localizedSystemFolderMeta(t, "root.platform.devices", "Devices", "");
    expect(meta.title).toBe("Devices");
  });

  it("uses displayName for user objects instead of missing i18n keys", () => {
    const meta = localizedSystemFolderMeta(
      t,
      "root.platform.devices.test-sensor",
      "Test Sensor",
      "",
    );
    expect(meta.title).toBe("Test Sensor");
    expect(meta.title).not.toContain("path.root__");
  });

  it("falls back to path segment when displayName is blank", () => {
    const meta = localizedSystemFolderMeta(t, "root.platform.dashboards.ops", "", "");
    expect(meta.title).toBe("ops");
  });
});
