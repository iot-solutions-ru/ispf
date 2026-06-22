import type { UseMutationResult, UseQueryResult } from "@tanstack/react-query";
import type { SecurityUserSummary } from "../../api/securityUsers";
import { copyToClipboard } from "./federationShared";

interface FederationTokensTabProps {
  usersQuery: UseQueryResult<SecurityUserSummary[], Error>;
  tokenUser: string;
  setTokenUser: (value: string) => void;
  tokenTtlHours: string;
  setTokenTtlHours: (value: string) => void;
  issuedToken: string | null;
  issuedTokenMeta: string | null;
  tokenCopyFeedback: string | null;
  setTokenCopyFeedback: (value: string | null) => void;
  tokenPanelError: string | null;
  tokenApiMissing: boolean;
  issueTokenMutation: UseMutationResult<unknown, Error, void, unknown>;
}

export default function FederationTokensTab({
  usersQuery,
  tokenUser,
  setTokenUser,
  tokenTtlHours,
  setTokenTtlHours,
  issuedToken,
  issuedTokenMeta,
  tokenCopyFeedback,
  setTokenCopyFeedback,
  tokenPanelError,
  tokenApiMissing,
  issueTokenMutation,
}: FederationTokensTabProps) {
  const userOptions = usersQuery.data ?? [];

  return (
    <div className="panel-card driver-config-form">
      <h4>Токен для федерации (этот узел)</h4>
      <p className="op-muted">
        Выпускает Bearer-сессию для выбранного пользователя (TTL по умолчанию 12 ч). Используйте на другом
        ISPF как токен авторизации узла, указывающего на этот инстанс.
      </p>
      <div className="form-grid">
        <label>
          Пользователь
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
          className="btn primary"
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
        <pre className="mono federation-code-block">{issuedToken}</pre>
      )}
    </div>
  );
}
