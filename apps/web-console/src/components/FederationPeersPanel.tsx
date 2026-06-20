import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import {
  createFederationPeer,
  deleteFederationPeer,
  fetchFederationPeers,
  probeFederationObject,
  syncFederationCatalog,
  type FederationPeerPayload,
} from "../api/federation";

interface FederationPeersPanelProps {
  canManage: boolean;
}

export default function FederationPeersPanel({ canManage }: FederationPeersPanelProps) {
  const queryClient = useQueryClient();
  const [form, setForm] = useState<FederationPeerPayload>({
    name: "",
    baseUrl: "http://127.0.0.1:8080",
    pathPrefix: "root.platform",
    enabled: true,
    description: "",
  });
  const [probePath, setProbePath] = useState("devices.demo-sensor-01");
  const [probePeerId, setProbePeerId] = useState<string>("");
  const [probeResult, setProbeResult] = useState<string | null>(null);
  const [formError, setFormError] = useState<string | null>(null);

  const peersQuery = useQuery({
    queryKey: ["federation-peers"],
    queryFn: fetchFederationPeers,
    enabled: canManage,
  });

  const createMutation = useMutation({
    mutationFn: () => {
      setFormError(null);
      if (!form.name.trim() || !form.baseUrl.trim()) {
        throw new Error("Укажите name и baseUrl");
      }
      return createFederationPeer({
        ...form,
        name: form.name.trim(),
        baseUrl: form.baseUrl.trim(),
      });
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["federation-peers"] });
      setForm((prev) => ({ ...prev, name: "", description: "" }));
    },
    onError: (error: Error) => setFormError(error.message),
  });

  const deleteMutation = useMutation({
    mutationFn: (id: string) => deleteFederationPeer(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["federation-peers"] }),
  });

  const syncMutation = useMutation({
    mutationFn: (id: string) => syncFederationCatalog(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["objects"] });
      queryClient.invalidateQueries({ queryKey: ["federation-peers"] });
    },
    onError: (error: Error) => setFormError(error.message),
  });

  const probeMutation = useMutation({
    mutationFn: () => {
      if (!probePeerId) {
        throw new Error("Выберите peer");
      }
      return probeFederationObject(probePeerId, probePath.trim());
    },
    onSuccess: (data) => setProbeResult(JSON.stringify(data, null, 2)),
    onError: (error: Error) => setProbeResult(error.message),
  });

  if (!canManage) {
    return <p className="op-muted">Federation доступна только admin.</p>;
  }

  return (
    <section className="federation-peers-panel">
      <header className="security-users-header">
        <div>
          <h3>Federation peers</h3>
          <p className="op-muted">
            Реестр удалённых ISPF-инстансов (PF-13 spike). Object path и service endpoint разделены:
            Loopback (тот же ISPF): оставьте <code>authToken</code> пустым — при Sync используется ваш Bearer-токен.
            Для удалённого peer с RBAC укажите service account token.
          </p>
        </div>
      </header>

      {peersQuery.error && <div className="op-alert op-alert-error">{String(peersQuery.error)}</div>}

      <table className="op-table security-users-table security-users-table-compact">
        <thead>
          <tr>
            <th>Имя</th>
            <th>Base URL</th>
            <th>Prefix</th>
            <th>Token</th>
            <th>Вкл.</th>
            <th />
          </tr>
        </thead>
        <tbody>
          {(peersQuery.data ?? []).map((peer) => (
            <tr key={peer.id}>
              <td><code>{peer.name}</code></td>
              <td>{peer.baseUrl}</td>
              <td><code>{peer.pathPrefix || "—"}</code></td>
              <td>{peer.hasAuthToken ? "да" : "нет"}</td>
              <td>{peer.enabled ? "да" : "нет"}</td>
              <td>
                <button
                  type="button"
                  className="btn compact"
                  disabled={syncMutation.isPending}
                  onClick={() => syncMutation.mutate(peer.id)}
                >
                  Sync
                </button>
                <button
                  type="button"
                  className="btn danger compact"
                  disabled={deleteMutation.isPending}
                  onClick={() => deleteMutation.mutate(peer.id)}
                >
                  Удалить
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
        <h4>Новый peer</h4>
        <div className="form-grid">
          <label>
            name *
            <input
              value={form.name}
              onChange={(e) => setForm((prev) => ({ ...prev, name: e.target.value }))}
            />
          </label>
          <label>
            baseUrl *
            <input
              value={form.baseUrl}
              onChange={(e) => setForm((prev) => ({ ...prev, baseUrl: e.target.value }))}
            />
          </label>
          <label>
            pathPrefix
            <input
              value={form.pathPrefix ?? ""}
              onChange={(e) => setForm((prev) => ({ ...prev, pathPrefix: e.target.value }))}
            />
          </label>
          <label>
            authToken
            <input
              type="password"
              value={form.authToken ?? ""}
              onChange={(e) => setForm((prev) => ({ ...prev, authToken: e.target.value }))}
            />
          </label>
          <label className="full">
            description
            <input
              value={form.description ?? ""}
              onChange={(e) => setForm((prev) => ({ ...prev, description: e.target.value }))}
            />
          </label>
        </div>
        <div className="form-actions">
          <button type="submit" className="btn primary" disabled={createMutation.isPending}>
            Добавить peer
          </button>
        </div>
        {formError && <p className="hint error">{formError}</p>}
      </form>

      <section className="federation-probe">
        <h4>Проверка proxy read</h4>
        <div className="form-grid">
          <label>
            peer
            <select
              value={probePeerId}
              onChange={(e) => setProbePeerId(e.target.value)}
            >
              <option value="">—</option>
              {(peersQuery.data ?? []).map((peer) => (
                <option key={peer.id} value={peer.id}>{peer.name}</option>
              ))}
            </select>
          </label>
          <label>
            path (локальный или относительный)
            <input value={probePath} onChange={(e) => setProbePath(e.target.value)} />
          </label>
        </div>
        <button
          type="button"
          className="btn"
          disabled={probeMutation.isPending}
          onClick={() => probeMutation.mutate()}
        >
          GET proxy object
        </button>
        {probeResult && (
          <pre className="mono federation-probe-result">{probeResult}</pre>
        )}
      </section>
    </section>
  );
}
