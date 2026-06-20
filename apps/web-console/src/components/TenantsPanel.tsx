import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { assignTenantUser, createTenant, fetchTenants } from "../api/tenants";

interface TenantsPanelProps {
  canManage: boolean;
  onSelectPath: (path: string) => void;
}

export default function TenantsPanel({ canManage, onSelectPath }: TenantsPanelProps) {
  const queryClient = useQueryClient();
  const [tenantId, setTenantId] = useState("");
  const [displayName, setDisplayName] = useState("");
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
      if (!tenantId.trim() || !displayName.trim()) {
        throw new Error("Укажите tenantId и displayName");
      }
      return createTenant({
        tenantId: tenantId.trim().toLowerCase(),
        displayName: displayName.trim(),
        enabled: true,
      });
    },
    onSuccess: (tenant) => {
      queryClient.invalidateQueries({ queryKey: ["tenants"] });
      queryClient.invalidateQueries({ queryKey: ["objects"] });
      setTenantId("");
      setDisplayName("");
      onSelectPath(tenant.platformPath);
    },
    onError: (error: Error) => setFormError(error.message),
  });

  const assignMutation = useMutation({
    mutationFn: () => assignTenantUser(assignTenantId, assignUsername.trim().toLowerCase()),
    onSuccess: () => setFormError(null),
    onError: (error: Error) => setFormError(error.message),
  });

  if (!canManage) {
    return <p className="op-muted">Multi-tenant доступен только admin.</p>;
  }

  return (
    <section className="tenants-panel">
      <header className="security-users-header">
        <div>
          <h3>Арендаторы (tenants)</h3>
          <p className="op-muted">
            Namespace <code>root.tenant.&#123;id&#125;.platform.*</code>. Пользователю с ролью operator можно
            назначить tenant — он увидит только своё поддерево.
          </p>
        </div>
      </header>

      <table className="op-table security-users-table security-users-table-compact">
        <thead>
          <tr>
            <th>tenantId</th>
            <th>Имя</th>
            <th>Platform path</th>
            <th>Вкл.</th>
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
              <td>{tenant.enabled ? "да" : "нет"}</td>
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
        <h4>Новый tenant</h4>
        <div className="form-grid">
          <label>
            tenantId *
            <input value={tenantId} onChange={(e) => setTenantId(e.target.value)} placeholder="acme" />
          </label>
          <label>
            displayName *
            <input value={displayName} onChange={(e) => setDisplayName(e.target.value)} placeholder="Acme Corp" />
          </label>
        </div>
        <button type="submit" className="btn primary" disabled={createMutation.isPending}>
          Создать tenant
        </button>
      </form>

      <section className="federation-probe">
        <h4>Назначить пользователю</h4>
        <div className="form-grid">
          <label>
            tenant
            <select value={assignTenantId} onChange={(e) => setAssignTenantId(e.target.value)}>
              <option value="">—</option>
              {(tenantsQuery.data ?? []).map((tenant) => (
                <option key={tenant.tenantId} value={tenant.tenantId}>{tenant.tenantId}</option>
              ))}
            </select>
          </label>
          <label>
            username
            <input value={assignUsername} onChange={(e) => setAssignUsername(e.target.value)} />
          </label>
        </div>
        <button
          type="button"
          className="btn"
          disabled={assignMutation.isPending || !assignTenantId}
          onClick={() => assignMutation.mutate()}
        >
          Назначить tenant
        </button>
      </section>

      {formError && <p className="hint error">{formError}</p>}
    </section>
  );
}
