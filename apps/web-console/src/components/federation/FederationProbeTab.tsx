import type { UseMutationResult, UseQueryResult } from "@tanstack/react-query";
import type { FederationPeer } from "../../api/federation";

interface FederationProbeTabProps {
  peersQuery: UseQueryResult<FederationPeer[], Error>;
  probePeerId: string;
  setProbePeerId: (value: string) => void;
  probePath: string;
  setProbePath: (value: string) => void;
  probeResult: string | null;
  probeMutation: UseMutationResult<unknown, Error, void, unknown>;
}

export default function FederationProbeTab({
  peersQuery,
  probePeerId,
  setProbePeerId,
  probePath,
  setProbePath,
  probeResult,
  probeMutation,
}: FederationProbeTabProps) {
  return (
    <div className="panel-card federation-probe">
      <h4>Проверка proxy read</h4>
      <p className="op-muted">
        Выполняет GET объекта через выбранный федеративный узел для проверки связности и авторизации.
      </p>
      <div className="form-grid">
        <label>
          Узел
          <select value={probePeerId} onChange={(e) => setProbePeerId(e.target.value)}>
            <option value="">— выберите узел —</option>
            {(peersQuery.data ?? []).map((peer) => (
              <option key={peer.id} value={peer.id}>{peer.name}</option>
            ))}
          </select>
        </label>
        <label>
          Путь (локальный или относительный)
          <input value={probePath} onChange={(e) => setProbePath(e.target.value)} />
        </label>
      </div>
      <div className="form-actions">
        <button
          type="button"
          className="btn primary"
          disabled={probeMutation.isPending || !probePeerId}
          onClick={() => probeMutation.mutate()}
        >
          {probeMutation.isPending ? "Проверка…" : "Проверить объект"}
        </button>
      </div>
      {probeResult && (
        <pre className="mono federation-probe-result">{probeResult}</pre>
      )}
    </div>
  );
}
