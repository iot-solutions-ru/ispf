import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import {
  connectOutboundAgent,
  createFederationPeer,
  createInboundRegistration,
  createOutboundAgent,
  deleteFederationPeer,
  deleteInboundRegistration,
  deleteOutboundAgent,
  fetchFederationPeers,
  fetchInboundRegistrations,
  fetchOutboundAgents,
  fetchRemoteFederationToken,
  fetchTunnelSessions,
  formatTokenExpiry,
  probeFederationObject,
  refreshPeerToken,
  syncFederationCatalog,
  type FederationPeerPayload,
} from "../api/federation";
import { fetchSecurityUsers, issueFederationToken } from "../api/securityUsers";
import { fetchPlatformInfo } from "../api";
import { getStoredSession } from "../auth/session";

interface FederationPeersPanelProps {
  canManage: boolean;
}

function defaultFederationBaseUrl(): string {
  if (import.meta.env.DEV) {
    return "http://127.0.0.1:8080";
  }
  return window.location.origin;
}

function copyToClipboard(text: string): Promise<void> {
  if (navigator.clipboard?.writeText) {
    return navigator.clipboard.writeText(text);
  }
  const textarea = document.createElement("textarea");
  textarea.value = text;
  textarea.style.position = "fixed";
  textarea.style.opacity = "0";
  document.body.appendChild(textarea);
  textarea.select();
  document.execCommand("copy");
  document.body.removeChild(textarea);
  return Promise.resolve();
}

export default function FederationPeersPanel({ canManage }: FederationPeersPanelProps) {
  const queryClient = useQueryClient();
  const [form, setForm] = useState<FederationPeerPayload>({
    name: "",
    baseUrl: defaultFederationBaseUrl(),
    pathPrefix: "root.platform",
    enabled: true,
    description: "",
  });
  const [probePath, setProbePath] = useState("devices.demo-sensor-01");
  const [probePeerId, setProbePeerId] = useState<string>("");
  const [probeResult, setProbeResult] = useState<string | null>(null);
  const [formError, setFormError] = useState<string | null>(null);
  const [tokenPanelError, setTokenPanelError] = useState<string | null>(null);
  const [syncFeedback, setSyncFeedback] = useState<string | null>(null);

  const [tokenUser, setTokenUser] = useState("admin");
  const [tokenTtlHours, setTokenTtlHours] = useState("12");
  const [issuedToken, setIssuedToken] = useState<string | null>(null);
  const [issuedTokenMeta, setIssuedTokenMeta] = useState<string | null>(null);
  const [tokenCopyFeedback, setTokenCopyFeedback] = useState<string | null>(null);

  const [remoteLoginUsername, setRemoteLoginUsername] = useState("admin");
  const [remoteLoginPassword, setRemoteLoginPassword] = useState("");
  const [useServiceAccount, setUseServiceAccount] = useState(false);
  const [serviceAccountUsername, setServiceAccountUsername] = useState("admin");
  const [serviceAccountPassword, setServiceAccountPassword] = useState("");

  const [inboundName, setInboundName] = useState("");
  const [inboundPathPrefix, setInboundPathPrefix] = useState("root.platform");
  const [issuedRegistrationCode, setIssuedRegistrationCode] = useState<string | null>(null);

  const [outboundForm, setOutboundForm] = useState({
    name: "",
    hubBaseUrl: defaultFederationBaseUrl(),
    registrationCode: "",
    pathPrefix: "root.platform",
  });

  const peersQuery = useQuery({
    queryKey: ["federation-peers"],
    queryFn: fetchFederationPeers,
    enabled: canManage,
  });

  const usersQuery = useQuery({
    queryKey: ["security-users"],
    queryFn: fetchSecurityUsers,
    enabled: canManage,
  });

  const platformInfoQuery = useQuery({
    queryKey: ["platform-info"],
    queryFn: fetchPlatformInfo,
    enabled: canManage,
  });

  const inboundQuery = useQuery({
    queryKey: ["federation-inbound-registrations"],
    queryFn: fetchInboundRegistrations,
    enabled: canManage,
  });

  const outboundQuery = useQuery({
    queryKey: ["federation-outbound-agents"],
    queryFn: fetchOutboundAgents,
    enabled: canManage,
    refetchInterval: 5000,
  });

  const tunnelsQuery = useQuery({
    queryKey: ["federation-tunnels"],
    queryFn: fetchTunnelSessions,
    enabled: canManage,
    refetchInterval: 5000,
  });

  const tokenApiSupported = platformInfoQuery.data
    ? platformInfoQuery.data.capabilities.includes("federation-issue-token") &&
      platformInfoQuery.data.capabilities.includes("federation-remote-token")
    : true;
  const tokenApiMissing = platformInfoQuery.isSuccess && !tokenApiSupported;
  const tunnelApiSupported = platformInfoQuery.data
    ? platformInfoQuery.data.capabilities.includes("federation-tunnel")
    : true;

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
        authMode: useServiceAccount ? "SERVICE_ACCOUNT" : "STATIC_TOKEN",
        authUsername: useServiceAccount ? serviceAccountUsername.trim() : undefined,
        authPassword: useServiceAccount ? serviceAccountPassword : undefined,
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
    onSuccess: (result) => {
      setSyncFeedback(
        `Catalog sync: ${result.localRoot} — created ${result.created}, updated ${result.updated} (remote ${result.remoteCount})`
      );
      setFormError(null);
      queryClient.invalidateQueries({ queryKey: ["objects"] });
      queryClient.invalidateQueries({ queryKey: ["federation-peers"] });
    },
    onError: (error: Error) => {
      setSyncFeedback(null);
      setFormError(error.message);
    },
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

  const issueTokenMutation = useMutation({
    mutationFn: () => {
      setTokenPanelError(null);
      const ttl = Number.parseInt(tokenTtlHours, 10);
      return issueFederationToken(tokenUser, Number.isFinite(ttl) && ttl > 0 ? ttl : undefined);
    },
    onSuccess: (data) => {
      setIssuedToken(data.token);
      const parts = [
        data.username ? `user: ${data.username}` : null,
        data.expiresAt ? `expires: ${data.expiresAt}` : null,
        data.roles?.length ? `roles: ${data.roles.join(", ")}` : null,
      ].filter(Boolean);
      setIssuedTokenMeta(parts.join(" · "));
      setTokenCopyFeedback(null);
      setTokenPanelError(null);
    },
    onError: (error: Error) => {
      setIssuedToken(null);
      setIssuedTokenMeta(null);
      setTokenPanelError(error.message);
    },
  });

  const refreshTokenMutation = useMutation({
    mutationFn: (peerId: string) => refreshPeerToken(peerId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["federation-peers"] });
      setSyncFeedback("Токен peer обновлён");
    },
    onError: (error: Error) => setFormError(error.message),
  });

  const createInboundMutation = useMutation({
    mutationFn: () => {
      if (!inboundName.trim()) {
        throw new Error("Укажите имя inbound registration");
      }
      return createInboundRegistration({
        name: inboundName.trim(),
        pathPrefix: inboundPathPrefix.trim() || "root.platform",
      });
    },
    onSuccess: (data) => {
      setIssuedRegistrationCode(data.registrationCode);
      setInboundName("");
      queryClient.invalidateQueries({ queryKey: ["federation-inbound-registrations"] });
    },
    onError: (error: Error) => setFormError(error.message),
  });

  const deleteInboundMutation = useMutation({
    mutationFn: (id: string) => deleteInboundRegistration(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["federation-inbound-registrations"] }),
  });

  const createOutboundMutation = useMutation({
    mutationFn: () => {
      if (!outboundForm.name.trim() || !outboundForm.hubBaseUrl.trim() || !outboundForm.registrationCode.trim()) {
        throw new Error("Укажите name, hub URL и registration code");
      }
      return createOutboundAgent({
        name: outboundForm.name.trim(),
        hubBaseUrl: outboundForm.hubBaseUrl.trim(),
        registrationCode: outboundForm.registrationCode.trim(),
        pathPrefix: outboundForm.pathPrefix.trim() || "root.platform",
      });
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["federation-outbound-agents"] });
      queryClient.invalidateQueries({ queryKey: ["federation-peers"] });
      setOutboundForm((prev) => ({ ...prev, name: "", registrationCode: "" }));
    },
    onError: (error: Error) => setFormError(error.message),
  });

  const connectOutboundMutation = useMutation({
    mutationFn: (id: string) => connectOutboundAgent(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["federation-outbound-agents"] }),
  });

  const deleteOutboundMutation = useMutation({
    mutationFn: (id: string) => deleteOutboundAgent(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["federation-outbound-agents"] }),
  });

  const remoteTokenMutation = useMutation({
    mutationFn: () => {
      setTokenPanelError(null);
      if (!form.baseUrl.trim()) {
        throw new Error("Укажите baseUrl peer");
      }
      if (!remoteLoginPassword) {
        throw new Error("Укажите пароль remote пользователя");
      }
      return fetchRemoteFederationToken({
        baseUrl: form.baseUrl.trim(),
        username: remoteLoginUsername.trim(),
        password: remoteLoginPassword,
      });
    },
    onSuccess: (data) => {
      setForm((prev) => ({ ...prev, authToken: data.token }));
      setRemoteLoginPassword("");
      setFormError(null);
      setTokenPanelError(null);
      setSyncFeedback(
        data.expiresAt
          ? `Токен получен с remote (${data.username ?? remoteLoginUsername}), expires ${data.expiresAt}`
          : `Токен получен с remote (${data.username ?? remoteLoginUsername})`
      );
    },
    onError: (error: Error) => setTokenPanelError(error.message),
  });

  if (!canManage) {
    return <p className="op-muted">Federation доступна только admin.</p>;
  }

  const userOptions = usersQuery.data ?? [];

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

      {tokenApiMissing && (
        <div className="op-alert op-alert-error">
          Backend без API federation-токенов (нужны capabilities federation-issue-token и federation-remote-token).
          Пересоберите и перезапустите <code>ispf-server</code>, затем обновите страницу.
        </div>
      )}

      <section className="federation-token-tools driver-config-form">
        <h4>Токен для federation (этот узел)</h4>
        <p className="op-muted">
          Выпускает Bearer-сессию для выбранного пользователя (TTL по умолчанию 12 ч). Используйте на другом
          ISPF как <code>authToken</code> peer, указывающего на этот инстанс.
        </p>
        <div className="form-grid">
          <label>
            пользователь
            <select
              value={tokenUser}
              onChange={(e) => setTokenUser(e.target.value)}
              disabled={userOptions.length === 0}
            >
              {userOptions.length === 0 && <option value={tokenUser}>{tokenUser}</option>}
              {userOptions.map((user) => (
                <option key={user.username} value={user.username}>
                  {user.username} ({user.roles.join(", ")})
                </option>
              ))}
            </select>
          </label>
          <label>
            TTL (часы)
            <input
              type="number"
              min={1}
              max={168}
              value={tokenTtlHours}
              onChange={(e) => setTokenTtlHours(e.target.value)}
            />
          </label>
        </div>
        <div className="form-actions">
          <button
            type="button"
            className="btn"
            disabled={issueTokenMutation.isPending || tokenApiMissing}
            onClick={() => issueTokenMutation.mutate()}
          >
            {issueTokenMutation.isPending ? "Выпуск…" : "Выпустить токен"}
          </button>
          {issuedToken && (
            <button
              type="button"
              className="btn compact"
              onClick={() => {
                copyToClipboard(issuedToken)
                  .then(() => setTokenCopyFeedback("Скопировано"))
                  .catch(() => setTokenCopyFeedback("Не удалось скопировать"));
              }}
            >
              Копировать
            </button>
          )}
        </div>
        {issuedTokenMeta && <p className="hint">{issuedTokenMeta}</p>}
        {tokenCopyFeedback && <p className="hint success">{tokenCopyFeedback}</p>}
        {tokenPanelError && <p className="hint error">{tokenPanelError}</p>}
        {issuedToken && (
          <pre className="mono federation-probe-result">{issuedToken}</pre>
        )}
      </section>

      <table className="op-table security-users-table security-users-table-compact">
        <thead>
          <tr>
            <th>Имя</th>
            <th>Base URL</th>
            <th>Prefix</th>
            <th>Auth</th>
            <th>Token</th>
            <th>Вкл.</th>
            <th />
          </tr>
        </thead>
        <tbody>
          {(peersQuery.data ?? []).map((peer) => (
            <tr key={peer.id}>
              <td>
                <code>{peer.name}</code>
                {peer.connectionMode === "TUNNEL_INBOUND" && (
                  <span className="badge">TUNNEL{peer.tunnelConnected ? "" : " (offline)"}</span>
                )}
              </td>
              <td>{peer.baseUrl}</td>
              <td><code>{peer.pathPrefix || "—"}</code></td>
              <td>
                {peer.authMode === "SERVICE_ACCOUNT" ? (
                  <>
                    <span className="badge">auto-refresh</span>
                    {" "}
                    <span className="op-muted">{formatTokenExpiry(peer.tokenExpiresAt)}</span>
                    {peer.authStatus === "FAILED" && (
                      <span className="badge danger">FAILED</span>
                    )}
                  </>
                ) : (
                  <span className="op-muted">static</span>
                )}
              </td>
              <td>{peer.hasAuthToken ? "да" : "нет"}</td>
              <td>{peer.enabled ? "да" : "нет"}</td>
              <td>
                {peer.authMode === "SERVICE_ACCOUNT" && (
                  <button
                    type="button"
                    className="btn compact"
                    disabled={refreshTokenMutation.isPending}
                    onClick={() => refreshTokenMutation.mutate(peer.id)}
                  >
                    Refresh
                  </button>
                )}
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
          <label className="full">
            <input
              type="checkbox"
              checked={useServiceAccount}
              onChange={(e) => setUseServiceAccount(e.target.checked)}
            />
            {" "}
            Auto-refresh (service account)
          </label>
          {useServiceAccount && (
            <>
              <label>
                auth username
                <input
                  value={serviceAccountUsername}
                  onChange={(e) => setServiceAccountUsername(e.target.value)}
                />
              </label>
              <label>
                auth password
                <input
                  type="password"
                  value={serviceAccountPassword}
                  onChange={(e) => setServiceAccountPassword(e.target.value)}
                />
              </label>
            </>
          )}
        </div>

        <section className="federation-remote-token">
          <h5>Получить authToken</h5>
          <p className="op-muted">
            Loopback: «Текущий сессионный токен». Remote: логин на peer через сервер (пароль не сохраняется).
          </p>
          <div className="form-actions">
            <button
              type="button"
              className="btn compact"
              onClick={() => {
                const session = getStoredSession();
                if (!session?.token) {
                  setFormError("Нет активной сессии в браузере");
                  return;
                }
                setForm((prev) => ({ ...prev, authToken: session.token }));
                setFormError(null);
                setSyncFeedback("Подставлен токен текущей сессии");
              }}
            >
              Текущий сессионный токен
            </button>
          </div>
          <div className="form-grid">
            <label>
              remote username
              <input
                value={remoteLoginUsername}
                onChange={(e) => setRemoteLoginUsername(e.target.value)}
              />
            </label>
            <label>
              remote password
              <input
                type="password"
                value={remoteLoginPassword}
                onChange={(e) => setRemoteLoginPassword(e.target.value)}
              />
            </label>
          </div>
          <div className="form-actions">
            <button
              type="button"
              className="btn"
              disabled={remoteTokenMutation.isPending || tokenApiMissing}
              onClick={() => remoteTokenMutation.mutate()}
            >
              {remoteTokenMutation.isPending ? "Получение…" : "Получить токен с remote"}
            </button>
          </div>
        </section>

        <div className="form-actions">
          <button type="submit" className="btn primary" disabled={createMutation.isPending}>
            Добавить peer
          </button>
        </div>
        {syncFeedback && <p className="hint success">{syncFeedback}</p>}
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

      {tunnelApiSupported && (
        <>
          <section className="federation-inbound driver-config-form">
            <h4>Inbound registrations (hub)</h4>
            <p className="op-muted">
              Выпустите одноразовый registration code для edge-агента за NAT. Код показывается один раз.
            </p>
            <div className="form-grid">
              <label>
                name *
                <input value={inboundName} onChange={(e) => setInboundName(e.target.value)} />
              </label>
              <label>
                pathPrefix
                <input value={inboundPathPrefix} onChange={(e) => setInboundPathPrefix(e.target.value)} />
              </label>
            </div>
            <div className="form-actions">
              <button
                type="button"
                className="btn primary"
                disabled={createInboundMutation.isPending}
                onClick={() => createInboundMutation.mutate()}
              >
                Создать registration code
              </button>
            </div>
            {issuedRegistrationCode && (
              <>
                <p className="hint success">Registration code (скопируйте сейчас):</p>
                <pre className="mono federation-probe-result">{issuedRegistrationCode}</pre>
                <button
                  type="button"
                  className="btn compact"
                  onClick={() => copyToClipboard(issuedRegistrationCode).then(() => setSyncFeedback("Code скопирован"))}
                >
                  Копировать code
                </button>
              </>
            )}
            <table className="op-table security-users-table security-users-table-compact">
              <thead>
                <tr>
                  <th>Имя</th>
                  <th>Prefix</th>
                  <th>Expires</th>
                  <th>Status</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {(inboundQuery.data ?? []).map((reg) => (
                  <tr key={reg.id}>
                    <td><code>{reg.name}</code></td>
                    <td><code>{reg.pathPrefix}</code></td>
                    <td>{new Date(reg.expiresAt).toLocaleString()}</td>
                    <td>{reg.consumedAt ? "consumed" : "pending"}</td>
                    <td>
                      <button
                        type="button"
                        className="btn danger compact"
                        disabled={deleteInboundMutation.isPending}
                        onClick={() => deleteInboundMutation.mutate(reg.id)}
                      >
                        Удалить
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </section>

          <section className="federation-outbound driver-config-form">
            <h4>Outbound agents (edge / NAT)</h4>
            <p className="op-muted">
              Исходящий WebSocket-туннель к public hub. Требуется <code>ispf.security.secrets-key</code> на edge.
            </p>
            <div className="form-grid">
              <label>
                name *
                <input
                  value={outboundForm.name}
                  onChange={(e) => setOutboundForm((prev) => ({ ...prev, name: e.target.value }))}
                />
              </label>
              <label>
                hub baseUrl *
                <input
                  value={outboundForm.hubBaseUrl}
                  onChange={(e) => setOutboundForm((prev) => ({ ...prev, hubBaseUrl: e.target.value }))}
                />
              </label>
              <label>
                registration code *
                <input
                  type="password"
                  value={outboundForm.registrationCode}
                  onChange={(e) => setOutboundForm((prev) => ({ ...prev, registrationCode: e.target.value }))}
                />
              </label>
              <label>
                pathPrefix
                <input
                  value={outboundForm.pathPrefix}
                  onChange={(e) => setOutboundForm((prev) => ({ ...prev, pathPrefix: e.target.value }))}
                />
              </label>
            </div>
            <div className="form-actions">
              <button
                type="button"
                className="btn primary"
                disabled={createOutboundMutation.isPending}
                onClick={() => createOutboundMutation.mutate()}
              >
                Добавить agent
              </button>
            </div>
            <table className="op-table security-users-table security-users-table-compact">
              <thead>
                <tr>
                  <th>Имя</th>
                  <th>Hub</th>
                  <th>Status</th>
                  <th>Peer</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {(outboundQuery.data ?? []).map((agent) => (
                  <tr key={agent.id}>
                    <td><code>{agent.name}</code></td>
                    <td>{agent.hubBaseUrl}</td>
                    <td>
                      {agent.tunnelStatus}
                      {agent.lastError && <span className="op-muted"> — {agent.lastError}</span>}
                    </td>
                    <td>{agent.linkedPeerId ?? "—"}</td>
                    <td>
                      <button
                        type="button"
                        className="btn compact"
                        disabled={connectOutboundMutation.isPending}
                        onClick={() => connectOutboundMutation.mutate(agent.id)}
                      >
                        Connect
                      </button>
                      <button
                        type="button"
                        className="btn danger compact"
                        disabled={deleteOutboundMutation.isPending}
                        onClick={() => deleteOutboundMutation.mutate(agent.id)}
                      >
                        Удалить
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
            {(tunnelsQuery.data ?? []).length > 0 && (
              <p className="hint">
                Active tunnel sessions on this hub: {(tunnelsQuery.data ?? []).length}
              </p>
            )}
          </section>
        </>
      )}
    </section>
  );
}
