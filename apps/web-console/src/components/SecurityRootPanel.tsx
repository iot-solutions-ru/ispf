import { useQuery } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { fetchSecurityRoles } from "../api/securityRoles";
import SecurityMfaPanel from "./SecurityMfaPanel";
import { SECURITY_ROLES_ROOT } from "../utils/securityRolePath";
import { SECURITY_USERS_ROOT } from "../utils/securityUserPath";

interface SecurityRootPanelProps {
  canManage: boolean;
  onSelectPath: (path: string) => void;
}

export default function SecurityRootPanel({ canManage, onSelectPath }: SecurityRootPanelProps) {
  const { t } = useTranslation(["security", "common"]);
  const rolesQuery = useQuery({
    queryKey: ["security-roles"],
    queryFn: fetchSecurityRoles,
    enabled: canManage,
  });

  const templates = (rolesQuery.data ?? []).filter((role) => role.template);

  return (
    <section className="security-users-panel">
      <header className="security-users-header">
        <div>
          <h3>{t("securityRoot.title")}</h3>
          <p className="op-muted">{t("securityRoot.subtitle")}</p>
        </div>
      </header>

      <div className="security-user-cards">
        <button
          type="button"
          className="security-user-card security-user-card--link"
          onClick={() => onSelectPath(SECURITY_USERS_ROOT)}
        >
          <h3 className="security-user-card-title">{t("users.title")}</h3>
          <p className="security-user-card-desc">{t("securityRoot.usersHint")}</p>
        </button>
        <button
          type="button"
          className="security-user-card security-user-card--link"
          onClick={() => onSelectPath(SECURITY_ROLES_ROOT)}
        >
          <h3 className="security-user-card-title">{t("roles.title")}</h3>
          <p className="security-user-card-desc">{t("securityRoot.rolesHint")}</p>
        </button>
      </div>

      <SecurityMfaPanel />

      <section className="modal-section">
        <h4>{t("roleTemplates.title")}</h4>
        <p className="op-muted">{t("roleTemplates.subtitle")}</p>
        {!canManage && <p className="op-muted">{t("roles.adminOnly")}</p>}
        {canManage && rolesQuery.isLoading && <p className="op-muted">{t("common:action.loading")}</p>}
        {canManage && rolesQuery.error && (
          <div className="op-alert op-alert-error">{String(rolesQuery.error)}</div>
        )}
        {canManage && !rolesQuery.isLoading && !rolesQuery.error && (
          <table className="op-table security-users-table security-users-table-compact">
            <thead>
              <tr>
                <th>{t("roles.column.name")}</th>
                <th>{t("roles.column.displayName")}</th>
                <th>{t("roles.column.description")}</th>
              </tr>
            </thead>
            <tbody>
              {templates.map((role) => (
                <tr key={role.name}>
                  <td>
                    <button
                      type="button"
                      className="link-btn"
                      onClick={() => onSelectPath(role.objectPath)}
                    >
                      <code>{role.name}</code>
                    </button>
                  </td>
                  <td>{role.displayName}</td>
                  <td>{role.description || t("common:empty.dash")}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>
    </section>
  );
}
