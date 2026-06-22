import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useState } from "react";
import {
  createFederationBind,
  fetchFederationPeers,
  probeFederationBind,
  rebindFederationObject,
  unbindFederationObject,
} from "../api/federation";
import type { ObjectSummary } from "../types";
import { isFederatedCatalogPath } from "../utils/federationPath";

interface FederationBindPanelProps {
  object: ObjectSummary;
  canManage: boolean;
  onChanged: () => void;
}

export default function FederationBindPanel({
  object,
  canManage,
  onChanged,
}: FederationBindPanelProps) {
  const queryClient = useQueryClient();
  const isMirror = isFederatedCatalogPath(object.path);
  const isBound = Boolean(object.federated);
  const [peerId, setPeerId] = useState(object.federationPeerId ?? "");
  const [remotePath, setRemotePath] = useState(object.federationRemotePath ?? "");
  const [placeParentPath, setPlaceParentPath] = useState("root.platform.devices");
  const [placeName, setPlaceName] = useState("");
  const [showPlaceLocally, setShowPlaceLocally] = useState(false);
  const [showUnbindConfirm, setShowUnbindConfirm] = useState(false);
  const [probeResult, setProbeResult] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const peersQuery = useQuery({
    queryKey: ["federation-peers"],
    queryFn: fetchFederationPeers,
    enabled: canManage,
  });

  useEffect(() => {
    setPeerId(object.federationPeerId ?? "");
    setRemotePath(object.federationRemotePath ?? "");
    setProbeResult(null);
    setError(null);
    setShowUnbindConfirm(false);
    if (isMirror && object.federationRemotePath) {
      const leaf = object.path.split(".").pop() ?? "local-copy";
      setPlaceName(leaf);
    }
  }, [object.path, object.federationPeerId, object.federationRemotePath, isMirror]);

  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: ["objects"] });
    queryClient.invalidateQueries({ queryKey: ["object", object.path] });
    queryClient.invalidateQueries({ queryKey: ["federation-binds"] });
    onChanged();
  };

  const bindMutation = useMutation({
    mutationFn: () =>
      createFederationBind({
        localPath: object.path,
        peerId,
        remotePath: remotePath.trim(),
      }),
    onSuccess: () => {
      setError(null);
      invalidate();
    },
    onError: (err: Error) => setError(err.message),
  });

  const rebindMutation = useMutation({
    mutationFn: () =>
      rebindFederationObject({
        localPath: object.path,
        peerId,
        remotePath: remotePath.trim(),
      }),
    onSuccess: () => {
      setError(null);
      invalidate();
    },
    onError: (err: Error) => setError(err.message),
  });

  const unbindMutation = useMutation({
    mutationFn: () => unbindFederationObject(object.path),
    onSuccess: () => {
      setError(null);
      setShowUnbindConfirm(false);
      invalidate();
    },
    onError: (err: Error) => setError(err.message),
  });

  const placeLocallyMutation = useMutation({
    mutationFn: () =>
      createFederationBind({
        parentPath: placeParentPath.trim(),
        name: placeName.trim(),
        peerId,
        remotePath: remotePath.trim(),
      }),
    onSuccess: () => {
      setError(null);
      setShowPlaceLocally(false);
      invalidate();
    },
    onError: (err: Error) => setError(err.message),
  });

  const probeMutation = useMutation({
    mutationFn: () => probeFederationBind(peerId, remotePath.trim()),
    onSuccess: (result) => {
      setError(null);
      setProbeResult(`${result.type}: ${result.displayName} — ${result.description || "без описания"}`);
    },
    onError: (err: Error) => setError(err.message),
  });

  if (!canManage) {
    if (!isBound) {
      return null;
    }
    return (
      <section className="panel panel-card federation-bind-panel">
        <h3>Федерация</h3>
        <span className="badge">Федеративный</span>
        <p className="hint">Объект привязан к удалённому узлу (только чтение метаданных привязки).</p>
        {object.federationRemotePath && (
          <p>
            Удалённый путь: <code>{object.federationRemotePath}</code>
          </p>
        )}
      </section>
    );
  }

  const peers = peersQuery.data ?? [];
  const peerOptions = peers.filter((peer) => peer.enabled);

  return (
    <section className="panel panel-card federation-bind-panel">
      <h3>Привязка к федерации</h3>
      {isBound && <span className="badge">Федеративный</span>}
      <p className="hint">
        Локальный путь <code>{object.path}</code> — канонический. Удалённый узел — источник данных и поведения.
      </p>

      {isMirror && (
        <div className="federation-bind-callout">
          Синхронизированный каталог — используйте «Разместить локально», чтобы создать локальную привязку.
        </div>
      )}

      {isMirror && !showPlaceLocally && (
        <div className="form-actions" style={{ marginBottom: "1rem" }}>
          <button type="button" className="btn primary" onClick={() => setShowPlaceLocally(true)}>
            Разместить локально…
          </button>
        </div>
      )}

      {showPlaceLocally && (
        <form
          className="form-grid"
          onSubmit={(e) => {
            e.preventDefault();
            placeLocallyMutation.mutate();
          }}
        >
          <label>
            Родительский путь
            <input value={placeParentPath} onChange={(e) => setPlaceParentPath(e.target.value)} />
          </label>
          <label>
            Имя узла
            <input value={placeName} onChange={(e) => setPlaceName(e.target.value)} required />
          </label>
          <label>
            Узел
            <select value={peerId} onChange={(e) => setPeerId(e.target.value)} required>
              <option value="">—</option>
              {peerOptions.map((peer) => (
                <option key={peer.id} value={peer.id}>
                  {peer.name}
                  {peer.tunnelConnected ? " (туннель)" : ""}
                </option>
              ))}
            </select>
          </label>
          <label className="full">
            Удалённый путь
            <input value={remotePath} onChange={(e) => setRemotePath(e.target.value)} required />
          </label>
          <div className="full form-actions">
            <button type="submit" className="btn primary" disabled={placeLocallyMutation.isPending}>
              Создать локальную привязку
            </button>
            <button type="button" className="btn" onClick={() => setShowPlaceLocally(false)}>
              Отмена
            </button>
          </div>
        </form>
      )}

      {!showPlaceLocally && (
        <>
          <div className="form-grid">
            <label>
              Узел
              <select value={peerId} onChange={(e) => setPeerId(e.target.value)}>
                <option value="">— выберите узел —</option>
                {peerOptions.map((peer) => (
                  <option key={peer.id} value={peer.id}>
                    {peer.name}
                    {peer.tunnelConnected ? " (туннель)" : ""}
                  </option>
                ))}
              </select>
            </label>
            <label className="full">
              Удалённый путь
              <input
                value={remotePath}
                onChange={(e) => setRemotePath(e.target.value)}
                placeholder="root.platform.devices.demo-sensor-01"
              />
            </label>
          </div>

          <div className="form-actions">
            <button
              type="button"
              className="btn"
              disabled={!peerId || !remotePath.trim() || probeMutation.isPending}
              onClick={() => probeMutation.mutate()}
            >
              Проверить
            </button>
            {!isBound && !isMirror && (
              <button
                type="button"
                className="btn primary"
                disabled={!peerId || !remotePath.trim() || bindMutation.isPending}
                onClick={() => bindMutation.mutate()}
              >
                Привязать к федерации
              </button>
            )}
            {isBound && (
              <>
                <button
                  type="button"
                  className="btn primary"
                  disabled={!peerId || !remotePath.trim() || rebindMutation.isPending}
                  onClick={() => rebindMutation.mutate()}
                >
                  Перепривязать
                </button>
                {!showUnbindConfirm ? (
                  <button
                    type="button"
                    className="btn danger"
                    disabled={unbindMutation.isPending}
                    onClick={() => setShowUnbindConfirm(true)}
                  >
                    Отвязать
                  </button>
                ) : (
                  <div className="federation-unbind-confirm">
                    <span>Снять привязку с «{object.displayName}»?</span>
                    <button
                      type="button"
                      className="btn danger compact"
                      disabled={unbindMutation.isPending}
                      onClick={() => unbindMutation.mutate()}
                    >
                      Подтвердить отвязку
                    </button>
                    <button
                      type="button"
                      className="btn compact"
                      onClick={() => setShowUnbindConfirm(false)}
                    >
                      Отмена
                    </button>
                  </div>
                )}
              </>
            )}
          </div>

          {probeResult && <pre className="mono federation-probe-result">{probeResult}</pre>}
        </>
      )}

      {error && <p className="hint error">{error}</p>}
      {(bindMutation.isSuccess || rebindMutation.isSuccess || unbindMutation.isSuccess) && (
        <p className="hint success">Привязка к федерации обновлена</p>
      )}
    </section>
  );
}
