import type { ObjectType } from "../../types";
import { APPLICATIONS_ROOT } from "../object/createObjectMode";
import { OPERATOR_APPS_ROOT } from "../operator/operatorAppsPath";
import { SECURITY_ROLES_ROOT } from "../security/securityRolePath";
import { SECURITY_USERS_ROOT } from "../security/securityUserPath";
import { ALERT_RULES_ROOT, CORRELATORS_ROOT } from "../automation/automationPath";
import { isSystemCatalogFolder } from "./systemFolderConfig";

/** Platform nodes seeded at bootstrap — not removable from the object tree. */
const NON_DELETABLE_PATHS = new Set([
  "root",
  "root.platform",
  "root.platform.devices",
  "root.platform.relative-blueprints",
  "root.platform.instance-types",
  "root.platform.absolute-blueprints",
  "root.platform.instances",
  "root.platform.dashboards",
  "root.platform.workflows",
  ALERT_RULES_ROOT,
  CORRELATORS_ROOT,
  APPLICATIONS_ROOT,
  OPERATOR_APPS_ROOT,
  "root.platform.security",
  SECURITY_USERS_ROOT,
  SECURITY_ROLES_ROOT,
]);

export function canDeleteObjectPath(path: string, objectType?: ObjectType): boolean {
  if (NON_DELETABLE_PATHS.has(path)) {
    return false;
  }
  if (isSystemCatalogFolder(path, objectType)) {
    return false;
  }
  return true;
}
