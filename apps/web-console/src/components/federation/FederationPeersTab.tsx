import type { UseMutationResult, UseQueryResult } from "@tanstack/react-query";
import type { FederationPeer, FederationPeerPayload } from "../../api/federation";
import { formatTokenExpiry } from "../../api/federation";
import { getStoredSession } from "../../auth/session";

interface FederationPeersTabProps {
  peersQuery: UseQueryResult<FederationPeer[], Error>;
  form: FederationPeerPayload;
  setForm: React.Dispatch<React.SetStateAction<FederationPeerPayload>>;
  useServiceAccount: boolean;
  setUseServiceAccount: (value: boolean) => void;
  serviceAccountUsername: string;
  setServiceAccountUsername: (value: string) => void;
  serviceAccountPassword: string;
  setServiceAccountPassword: (value: string) => void;
  remoteLoginUsername: string;
  setRemoteLoginUsername: (value: string) => void;
  remoteLoginPassword: string;
  setRemoteLoginPassword: (value: string) => void;
  formError: string | null;
  syncFeedback: string | null;
  tokenApiMissing: boolean;
  createMutation: UseMutationResult<unknown, Error, void, unknown>;
  deleteMutation: UseMutationResult<unknown, Error, string, unknown>;
  syncMutation: UseMutationResult<unknown, Error, string, unknown>;
  refreshTokenMutation: UseMutationResult<unknown, Error, string, unknown>;
  remoteTokenMutation: UseMutationResult<unknown, Error, void, unknown>;
  setFormError: (value: string | null) => void;
  setSyncFeedback: (value: string | null) => void;
}

function authBadge(peer: FederationPeer): React.ReactNode {
  if (peer.authMode === "SERVICE_ACCOUNT") {
    return (
      <>
        <span className="badge">авто</span>
        {" "}
        <span className="op-muted">{formatTokenExpiry(peer.tokenExpiresAt)}</span>
        {peer.authStatus === "FAILED" ? (
          <span className="badge danger" title="Ошибка авторизации">Ошибка</span>
        ) : (
          <span className="badge ok">OK</span>
        )}
      </>
    );
  }
  return peer.hasAuthToken ? (
    <span className="badge ok">OK</span>
  ) : (
    <span className="badge">нет токена</span>
  );
}

export default function FederationPeersTab({
  peersQuery,
  form,
  setForm,
  useServiceAccount,
  setUseServiceAccount,
  serviceAccountUsername,
  setServiceAccountUsername,
  serviceAccountPassword,
  setServiceAccountPassword,
  remoteLoginUsername,
  setRemoteLoginUsername,
  remoteLoginPassword,
  setRemoteLoginPassword,
  formError,
  syncFeedback,
  tokenApiMissing,
  createMutation,
  deleteMutation,
  syncMutation,
  refreshTokenMutation,
  remoteTokenMutation,
  setFormError,
  setSyncFeedback,
}: FederationPeersTabProps) {
  const peers = peersQuery.data ?? [];

  return (
    <>
      {syncFeedback && <div className="op-alert op-alert-success">{syncFeedback}</div>}
      {formError && <div className="op-alert op-alert-error">{formError}</div>}

      <div className="panel-card">
        <h4>Зарегистрированные узлы</h4>
        {peers.length === 0 ? (
          <p className="federation-empty-state">Нет зарегистрированных узлов</p>
        ) : (
          <table className="op-table security-users-table security-users-table-compact">
            <thead>
              <tr>
                <th>Имя</th>
                <th>URL узла</th>
                <th>Префикс</th>
                <th>Авторизация</th>
                <th>Токен</th>
                <th>Вкл.</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {peers.map((peer) => (
                <tr key={peer.id}>
                  <td>
                    <code>{peer.name}</code>
                    {peer.connectionMode === "TUNNEL_INBOUND" && (
                      <span className="badge">
                        ТУННЕЛЬ{peer.tunnelConnected ? "" : " (офлайн)"}
                      </span>
                    )}
                  </td>
                  <td>{peer.baseUrl}</td>
                  <td><code>{peer.pathPrefix || "—"}</code></td>
                  <td>{authBadge(peer)}</td>
                  <td>{peer.hasAuthToken ? "да" : "нет"}</td>
                  <td>{peer.enabled ? "да" : "нет"}</td>
                  <td>
                    <div className="federation-peer-actions">
                      {peer.authMode === "SERVICE_ACCOUNT" && (
                        <button
                          type="button"
                          className="btn compact"
                          disabled={refreshTokenMutation.isPending}
                          onClick={() => refreshTokenMutation.mutate(peer.id)}
                        >
                          Обновить токен
                        </button>
                      )}
                      <button
                        type="button"
                        className="btn compact"
                        disabled={syncMutation.isPending}
                        onClick={() => syncMutation.mutate(peer.id)}
                      >
                        Синхронизировать каталог
                      </button>
                      <button
                        type="button"
                        className="btn danger compact"
                        disabled={deleteMutation.isPending}
                        onClick={() => deleteMutation.mutate(peer.id)}
                      >
                        Удалить
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      <form
        className="panel-card driver-config-form"
        onSubmit={(e) => {
          e.preventDefault();
          createMutation.mutate();
        }}
      >
        <h4>Новый узел</h4>
        <p className="op-muted">
          Loopback (тот же ISPF): оставьте токен пустым — при синхронизации используется ваш Bearer-токен.
          Для удалённого узла с RBAC укажите service account token.
        </p>
        <div className="form-grid">
          <label>
            Имя *
            <input
              value={form.name}
              onChange={(e) => setForm((prev) => ({ ...prev, name: e.target.value }))}
            />
          </label>
          <label>
            URL узла *
            <input
              value={form.baseUrl}
              onChange={(e) => setForm((prev) => ({ ...prev, baseUrl: e.target.value }))}
            />
          </label>
          <label>
            Префикс пути
            <input
              value={form.pathPrefix ?? ""}
              onChange={(e) => setForm((prev) => ({ ...prev, pathPrefix: e.target.value }))}
            />
          </label>
          <label>
            Токен авторизации
            <input
              type="password"
              value={form.authToken ?? ""}
              onChange={(e) => setForm((prev) => ({ ...prev, authToken: e.target.value }))}
            />
          </label>
          <label className="full">
            Описание
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
            Автообновление токена (service account)
          </label>
          {useServiceAccount && (
            <>
              <label>
                Имя пользователя
                <input
                  value={serviceAccountUsername}
                  onChange={(e) => setServiceAccountUsername(e.target.value)}
                />
              </label>
              <label>
                Пароль
                <input
                  type="password"
                  value={serviceAccountPassword}
                  onChange={(e) => setServiceAccountPassword(e.target.value)}
                />
              </label>
            </>
          )}
        </div>

        <section className="federation-remote-token panel-card">
          <h5>Получить токен авторизации</h5>
          <p className="op-muted">
            Loopback: «Текущий сессионный токен». Удалённый узел: логин через сервер (пароль не сохраняется).
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
              Имя пользователя (удалённый)
              <input
                value={remoteLoginUsername}
                onChange={(e) => setRemoteLoginUsername(e.target.value)}
              />
            </label>
            <label>
              Пароль (удалённый)
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
              {remoteTokenMutation.isPending ? "Получение…" : "Получить токен с удалённого узла"}
            </button>
          </div>
        </section>

        <div className="form-actions">
          <button type="submit" className="btn primary" disabled={createMutation.isPending}>
            {createMutation.isPending ? "Добавление…" : "Добавить узел"}
          </button>
        </div>
      </form>
    </>
  );
}
