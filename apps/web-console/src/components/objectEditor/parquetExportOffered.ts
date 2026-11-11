import type { PlatformInfo } from "../../types";

/** Parquet is optional. Offer it only when this server reports the module is loaded. */
export function parquetExportOffered(
  info: Pick<PlatformInfo, "parquetExportAvailable"> | undefined,
): boolean {
  return info?.parquetExportAvailable === true;
}
