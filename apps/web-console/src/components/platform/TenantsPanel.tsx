import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import { assignTenantUser, createTenant, deleteTenant, fetchTenants } from "../../api/tenants";

interface TenantsPanelProps {
  canManage: boolean;
  onSelectPath: (path: string) => void;
}

interface CreatedAdminCredentials {
  tenantId: string;
  username: string;
  password?: string;
  platformPath: string;
}

export default function TenantsPanel({ canManage, onSelectPath }: TenantsPanelProps) {
  const { t } = useTranslation(["security", "common"]);
  const queryClient = useQueryClient();
  const [tenantId, setTenantId] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [adminPassword, setAdminPassword] = useState("");
  const [createdAdmin, setCreatedAdmin] = useState<CreatedAdminCredentials | null>(null);
  const [copyHint, setCopyHint] = useState<string | null>(null);
  const [assignUsername, setAssignUsername] = useState("operator");
  const [assignTenantId, setAssignTenantId] = useState("");
  const [formError, setFormError] = useState<string | null>(null);

  const tenantsQuery = useQuery({
    queryKey: ["tenants"],
    queryFn: fetchTenants,
    enabled: canManage,
  });

  const createMutation = useMutation({
    mutationFn: () => {
      setFormError(null);
      setCopyHint(null);
      if (!tenantId.trim() || !displayName.trim()) {
        throw new Error(t("tenants.error.required"));
      }
      return createTenant({
        tenantId: tenantId.trim().toLowerCase(),
        displayName: displayName.trim(),
        enabled: true,
        adminPassword: adminPassword.trim() || undefined,
      });
    },
    onSuccess: (tenant) => {
      queryClient.invalidateQueries({ queryKey: ["tenants"] });
      queryClient.invalidateQueries({ queryKey: ["objects"] });
      const id = tenant.tenantId;
      setTenantId("");
      setDisplayName("");
      setAdminPassword("");
      // Stay on Tenants panel so one-time credentials remain visible.
      if (tenant.adminUsername) {
        setCreatedAdmin({
          tenantId: id,
          username: tenant.adminUsername,
          password: tenant.adminPassword,
          platformPath: tenant.platformPath,
        });
      } else {
        setCreatedAdmin(null);
      }
    },
    onError: (error: Error) => setFormError(error.message),
  });

  const assignMutation = useMutation({
    mutationFn: () => assignTenantUser(assignTenantId, assignUsername.trim().toLowerCase()),
    onSuccess: () => setFormError(null),
    onError: (error: Error) => setFormError(error.message),
  });

  const deleteMutation = useMutation({
    mutationFn: (id: string) => deleteTenant(id),
    onSuccess: (_data, id) => {
      queryClient.invalidateQueries({ queryKey: ["tenants"] });
      queryClient.invalidateQueries({ queryKey: ["objects"] });
      if (createdAdmin?.tenantId === id) {
        setCreatedAdmin(null);
      }
      if (assignTenantId === id) {
        setAssignTenantId("");
      }
      setFormError(null);
    },
    onError: (error: Error) => setFormError(error.message),
  });

  const confirmDelete = (id: string) => {
    if (!window.confirm(t("tenants.deleteConfirm", { tenantId: id }))) {
      return;
    }
    deleteMutation.mutate(id);
  };

  const copyCredentials = async () => {
    if (!createdAdmin) {
      return;
    }
    const text = createdAdmin.password
      ? `${createdAdmin.username}\n${createdAdmin.password}`
      : createdAdmin.username;
    try {
      await navigator.clipboard.writeText(text);
      setCopyHint(t("tenants.credentialsCopied"));
    } catch {
      setCopyHint(t("common:action.copyFailed"));
    }
  };

  if (!canManage) {
    return <p className="op-muted">{t("tenants.adminOnly")}</p>;
  }

  return (
    <section className="tenants-panel">
      <header className="security-users-header">
        <div>
          <h3>{t("tenants.title")}</h3>
          <p className="op-muted">{t("tenants.subtitle")}</p>
        </div>
      </header>

      {createdAdmin && (
        <div className="tenants-credentials-banner" role="status">
          <h4>{t("tenants.credentialsTitle", { tenantId: createdAdmin.tenantId })}</h4>
          <p className="hint">{t("tenants.credentialsWarning")}</p>
          <dl className="tenants-credentials-fields">
            <div>
              <dt>{t("tenants.field.adminUsername")}</dt>
              <dd><code>{createdAdmin.username}</code></dd>
            </div>
            {createdAdmin.password ? (
              <div>
                <dt>{t("tenants.field.adminPasswordOnce")}</dt>
                <dd><code>{createdAdmin.password}</code></dd>
              </div>
            ) : null}
          </dl>
          <div className="tenants-credentials-actions">
            <button type="button" className="btn primary" onClick={() => void copyCredentials()}>
              {t("tenants.copyCredentials")}
            </button>
            <button
              type="button"
              className="btn"
              onClick={() => onSelectPath(createdAdmin.platformPath)}
            >
              {t("tenants.openPlatform")}
            </button>
            <button type="button" className="btn" onClick={() => setCreatedAdmin(null)}>
              {t("tenants.dismissCredentials")}
            </button>
          </div>
          {copyHint && <p className="hint">{copyHint}</p>}
        </div>
      )}

      {formError && <p className="hint error">{formError}</p>}

      <table className="op-table security-users-table security-users-table-compact">
        <thead>
          <tr>
            <th>{t("tenants.column.tenantId")}</th>
            <th>{t("tenants.column.displayName")}</th>
            <th>{t("tenants.column.platformPath")}</th>
            <th>{t("tenants.column.enabled")}</th>
            <th>{t("tenants.column.actions")}</th>
          </tr>
        </thead>
        <tbody>
          {(tenantsQuery.data ?? []).map((tenant) => (
            <tr key={tenant.tenantId}>
              <td>
                <button
                  type="button"
                  className="link-btn"
                  onClick={() => onSelectPath(tenant.platformPath)}
                >
                  <code>{tenant.tenantId}</code>
                </button>
              </td>
              <td>{tenant.displayName}</td>
              <td><code>{tenant.platformPath}</code></td>
              <td>{tenant.enabled ? t("common:action.yes") : t("common:action.no")}</td>
              <td>
                <button
                  type="button"
                  className="btn btn-sm"
                  disabled={deleteMutation.isPending}
                  onClick={() => confirmDelete(tenant.tenantId)}
                >
                  {t("tenants.deleteTenant")}
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>

      <form
        className="driver-config-form"
        onSubmit={(e) => {
          e.preventDefault();
          createMutation.mutate();
        }}
      >
        <h4>{t("tenants.newTenant")}</h4>
        <div className="form-grid">
          <label>
            {t("tenants.field.tenantId")}
            <input
              value={tenantId}
              onChange={(e) => setTenantId(e.target.value)}
              placeholder={t("tenants.field.tenantIdHint")}
            />
          </label>
          <label>
            {t("tenants.field.displayName")}
            <input
              value={displayName}
              onChange={(e) => setDisplayName(e.target.value)}
              placeholder={t("tenants.field.displayNameHint")}
            />
          </label>
          <label>
            {t("tenants.field.adminPassword")}
            <input
              type="password"
              value={adminPassword}
              onChange={(e) => setAdminPassword(e.target.value)}
              placeholder={t("tenants.field.adminPasswordHint")}
              autoComplete="new-password"
            />
          </label>
        </div>
        <p className="op-muted">{t("tenants.localAdminHint")}</p>
        <button type="submit" className="btn primary" disabled={createMutation.isPending}>
          {t("tenants.createTenant")}
        </button>
      </form>

      <section className="federation-probe">
        <h4>{t("tenants.assignUser")}</h4>
        <div className="form-grid">
          <label>
            {t("tenants.field.tenant")}
            <select value={assignTenantId} onChange={(e) => setAssignTenantId(e.target.value)}>
              <option value="">{t("common:empty.dash")}</option>
              {(tenantsQuery.data ?? []).map((tenant) => (
                <option key={tenant.tenantId} value={tenant.tenantId}>{tenant.tenantId}</option>
              ))}
            </select>
          </label>
          <label>
            {t("tenants.field.username")}
            <input value={assignUsername} onChange={(e) => setAssignUsername(e.target.value)} />
          </label>
        </div>
        <button
          type="button"
          className="btn"
          disabled={assignMutation.isPending || !assignTenantId}
          onClick={() => assignMutation.mutate()}
        >
          {t("tenants.assignTenant")}
        </button>
      </section>
    </section>
  );
}
