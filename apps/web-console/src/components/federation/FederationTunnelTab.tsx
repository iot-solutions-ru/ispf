import type { UseMutationResult, UseQueryResult } from "@tanstack/react-query";
import type {
  InboundRegistration,
  OutboundAgent,
  TunnelSession,
} from "../../api/federation";
import { copyToClipboard } from "./federationShared";

interface FederationTunnelTabProps {
  inboundQuery: UseQueryResult<InboundRegistration[], Error>;
  outboundQuery: UseQueryResult<OutboundAgent[], Error>;
  tunnelsQuery: UseQueryResult<TunnelSession[], Error>;
  inboundName: string;
  setInboundName: (value: string) => void;
  inboundPathPrefix: string;
  setInboundPathPrefix: (value: string) => void;
  issuedRegistrationCode: string | null;
  outboundForm: {
    name: string;
    hubBaseUrl: string;
    registrationCode: string;
    pathPrefix: string;
  };
  setOutboundForm: React.Dispatch<
    React.SetStateAction<{
      name: string;
      hubBaseUrl: string;
      registrationCode: string;
      pathPrefix: string;
    }>
  >;
  secretsKeyInput: string;
  setSecretsKeyInput: (value: string) => void;
  secretsKeyError: string | null;
  secretsKeyFeedback: string | null;
  secretsKeyConfigured: boolean;
  secretsKeySource: string;
  secretsKeyUiConfigurable: boolean;
  formError: string | null;
  setSyncFeedback: (value: string | null) => void;
  createInboundMutation: UseMutationResult<unknown, Error, void, unknown>;
  deleteInboundMutation: UseMutationResult<unknown, Error, string, unknown>;
  createOutboundMutation: UseMutationResult<unknown, Error, void, unknown>;
  connectOutboundMutation: UseMutationResult<unknown, Error, string, unknown>;
  deleteOutboundMutation: UseMutationResult<unknown, Error, string, unknown>;
  configureSecretsKeyMutation: UseMutationResult<unknown, Error, void, unknown>;
}

export default function FederationTunnelTab({
  inboundQuery,
  outboundQuery,
  tunnelsQuery,
  inboundName,
  setInboundName,
  inboundPathPrefix,
  setInboundPathPrefix,
  issuedRegistrationCode,
  outboundForm,
  setOutboundForm,
  secretsKeyInput,
  setSecretsKeyInput,
  secretsKeyError,
  secretsKeyFeedback,
  secretsKeyConfigured,
  secretsKeySource,
  secretsKeyUiConfigurable,
  formError,
  setSyncFeedback,
  createInboundMutation,
  deleteInboundMutation,
  createOutboundMutation,
  connectOutboundMutation,
  deleteOutboundMutation,
  configureSecretsKeyMutation,
}: FederationTunnelTabProps) {
  return (
    <>
      {formError && <div className="op-alert op-alert-error">{formError}</div>}

      <section className="panel-card driver-config-form">
        <h4>Входящие регистрации (hub)</h4>
        <p className="op-muted">
          Выпустите одноразовый код регистрации для edge-агента за NAT. Код показывается один раз.
        </p>
        <div className="form-grid">
          <label>
            Имя *
            <input value={inboundName} onChange={(e) => setInboundName(e.target.value)} />
          </label>
          <label>
            Префикс пути
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
            Создать код регистрации
          </button>
        </div>
        {issuedRegistrationCode && (
          <>
            <p className="hint success">Код регистрации (скопируйте сейчас):</p>
            <pre className="mono federation-code-block">{issuedRegistrationCode}</pre>
            <button
              type="button"
              className="btn compact"
              onClick={() =>
                copyToClipboard(issuedRegistrationCode).then(() =>
                  setSyncFeedback("Код скопирован")
                )
              }
            >
              Копировать код
            </button>
          </>
        )}
        <table className="op-table security-users-table security-users-table-compact">
          <thead>
            <tr>
              <th>Имя</th>
              <th>Префикс</th>
              <th>Истекает</th>
              <th>Статус</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {(inboundQuery.data ?? []).map((reg) => (
              <tr key={reg.id}>
                <td><code>{reg.name}</code></td>
                <td><code>{reg.pathPrefix}</code></td>
                <td>{new Date(reg.expiresAt).toLocaleString()}</td>
                <td>{reg.consumedAt ? "использован" : "ожидает"}</td>
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

      <section className="panel-card driver-config-form">
        <h4>Исходящие агенты (edge / NAT)</h4>
        <p className="op-muted">
          Исходящий WebSocket-туннель к public hub. На edge нужен ключ шифрования для хранения кода
          регистрации и session token (<code>ispf.security.secrets-key</code>).
        </p>

        <section className="panel-card federation-secrets-key">
          <h5>Ключ шифрования edge (ispf.security.secrets-key)</h5>
          {secretsKeyConfigured ? (
            <div className="op-alert op-alert-success">
              Ключ настроен
              {secretsKeySource === "YAML" && " — задан в application.yml или переменной ISPF_SECURITY_SECRETS_KEY"}
              {secretsKeySource === "DATABASE" && " — сохранён через Web Console"}
              .
            </div>
          ) : (
            <div className="op-alert op-alert-error">
              Ключ не настроен. Укажите его ниже или задайте{" "}
              <code>ispf.security.secrets-key</code> в конфиге сервера и перезапустите ISPF.
            </div>
          )}
          {secretsKeyFeedback && <div className="op-alert op-alert-success">{secretsKeyFeedback}</div>}
          {secretsKeyError && <div className="op-alert op-alert-error">{secretsKeyError}</div>}
          {secretsKeyUiConfigurable && !secretsKeyConfigured && (
            <>
              <div className="form-grid">
                <label>
                  Ключ шифрования *
                  <input
                    type="password"
                    autoComplete="new-password"
                    placeholder="минимум 16 символов"
                    value={secretsKeyInput}
                    onChange={(e) => setSecretsKeyInput(e.target.value)}
                  />
                </label>
              </div>
              <p className="hint">
                Сохраните ключ в надёжном месте. При смене ключа существующие исходящие агенты перестанут работать.
              </p>
              <div className="form-actions">
                <button
                  type="button"
                  className="btn primary"
                  disabled={configureSecretsKeyMutation.isPending}
                  onClick={() => configureSecretsKeyMutation.mutate()}
                >
                  Сохранить ключ
                </button>
              </div>
            </>
          )}
          {secretsKeyUiConfigurable && secretsKeyConfigured && secretsKeySource === "DATABASE" && (
            <p className="hint">
              Чтобы сменить ключ, удалите все исходящие агенты и сохраните новый ключ шифрования.
            </p>
          )}
        </section>

        <div className="form-grid">
          <label>
            Имя *
            <input
              value={outboundForm.name}
              onChange={(e) => setOutboundForm((prev) => ({ ...prev, name: e.target.value }))}
            />
          </label>
          <label>
            URL hub *
            <input
              value={outboundForm.hubBaseUrl}
              onChange={(e) => setOutboundForm((prev) => ({ ...prev, hubBaseUrl: e.target.value }))}
            />
          </label>
          <label>
            Код регистрации *
            <input
              type="password"
              value={outboundForm.registrationCode}
              onChange={(e) => setOutboundForm((prev) => ({ ...prev, registrationCode: e.target.value }))}
            />
          </label>
          <label>
            Префикс пути
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
            disabled={createOutboundMutation.isPending || !secretsKeyConfigured}
            onClick={() => createOutboundMutation.mutate()}
          >
            Добавить агент
          </button>
        </div>
        <table className="op-table security-users-table security-users-table-compact">
          <thead>
            <tr>
              <th>Имя</th>
              <th>Hub</th>
              <th>Статус</th>
              <th>Узел</th>
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
                  <div className="federation-peer-actions">
                    <button
                      type="button"
                      className="btn compact"
                      disabled={connectOutboundMutation.isPending}
                      onClick={() => connectOutboundMutation.mutate(agent.id)}
                    >
                      Подключить
                    </button>
                    <button
                      type="button"
                      className="btn danger compact"
                      disabled={deleteOutboundMutation.isPending}
                      onClick={() => deleteOutboundMutation.mutate(agent.id)}
                    >
                      Удалить
                    </button>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {(tunnelsQuery.data ?? []).length > 0 && (
          <p className="hint">
            Активных туннельных сессий на этом hub: {(tunnelsQuery.data ?? []).length}
          </p>
        )}
      </section>
    </>
  );
}
