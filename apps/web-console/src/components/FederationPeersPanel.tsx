import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import {
  createFederationPeer,
  deleteFederationPeer,
  fetchFederationPeers,
  fetchRemoteFederationToken,
  probeFederationObject,
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

  const tokenApiSupported = platformInfoQuery.data
    ? platformInfoQuery.data.capabilities.includes("federation-issue-token") &&
      platformInfoQuery.data.capabilities.includes("federation-remote-token")
    : true;
  const tokenApiMissing = platformInfoQuery.isSuccess && !tokenApiSupported;

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
    </section>
  );
}
