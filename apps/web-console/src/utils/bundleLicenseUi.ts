export interface ManifestLicenseSummary {
  present: boolean;
  bundleId?: string;
  installationId?: string;
  expiresAt?: string;
}

export function parseManifestLicense(manifest: unknown): ManifestLicenseSummary {
  if (!manifest || typeof manifest !== "object") {
    return { present: false };
  }
  const license = (manifest as Record<string, unknown>).license;
  if (!license || typeof license !== "object") {
    return { present: false };
  }
  const record = license as Record<string, unknown>;
  return {
    present: true,
    bundleId: stringOrUndefined(record.bundleId),
    installationId: stringOrUndefined(record.installationId),
    expiresAt: stringOrUndefined(record.expiresAt),
  };
}

export function isLicenseRelatedError(message: string): boolean {
  const lower = message.toLowerCase();
  return (
    lower.includes("license")
    || lower.includes("installationid")
    || lower.includes("contentsha256")
    || lower.includes("signed license")
    || lower.includes("public-key-pem")
    || lower.includes("403")
  );
}

/** i18n key suffix under platform:bundle.license.errorHint.* */
export function licenseErrorHintKey(message: string): string {
  const lower = message.toLowerCase();
  if (lower.includes("installationid")) {
    return "installationId";
  }
  if (lower.includes("contentsha256")) {
    return "contentSha256";
  }
  if (lower.includes("expired")) {
    return "expired";
  }
  if (lower.includes("signature") || lower.includes("public-key-pem")) {
    return "signature";
  }
  if (lower.includes("signed license") || lower.includes("require-signed")) {
    return "requireSigned";
  }
  if (lower.includes("bundleid")) {
    return "bundleId";
  }
  return "generic";
}

function stringOrUndefined(value: unknown): string | undefined {
  if (typeof value !== "string" || !value.trim()) {
    return undefined;
  }
  return value.trim();
}
