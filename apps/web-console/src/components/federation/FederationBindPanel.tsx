import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Alert, Button, Form, Input, Select, Space, Tag, Typography } from "antd";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import {
  createFederationBind,
  fetchFederationPeers,
  probeFederationBind,
  rebindFederationObject,
  unbindFederationObject,
} from "../../api/federation";
import type { ObjectSummary } from "../../types";
import {
  catalogMirrorToRemoteSubtree,
  findPeerForCatalogMirrorPath,
  isFederatedCatalogPath,
} from "../../utils/federation/federationPath";
import FederationCatalogSyncDialog from "./FederationCatalogSyncDialog";

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
  const { t } = useTranslation(["federation", "common"]);
  const queryClient = useQueryClient();
  const isMirror = isFederatedCatalogPath(object.path);
  const isBound = Boolean(object.federated);
  const [peerId, setPeerId] = useState(object.federationPeerId ?? "");
  const [remotePath, setRemotePath] = useState(object.federationRemotePath ?? "");
  const [placeParentPath, setPlaceParentPath] = useState("root.platform.devices");
  const [placeName, setPlaceName] = useState("");
  const [showPlaceLocally, setShowPlaceLocally] = useState(false);
  const [showUnbindConfirm, setShowUnbindConfirm] = useState(false);
  const [syncSubtreeOpen, setSyncSubtreeOpen] = useState(false);
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
      setProbeResult(
        `${result.type}: ${result.displayName} — ${result.description || t("bind.probeNoDescription")}`
      );
    },
    onError: (err: Error) => setError(err.message),
  });

  if (!canManage) {
    if (!isBound) {
      return null;
    }
    return (
      <section className="panel panel-card federation-bind-panel">
        <Space orientation="vertical" size="small" style={{ width: "100%" }}>
        <Typography.Title level={3} style={{ margin: 0 }}>
          {t("bind.titleReadonly")}
        </Typography.Title>
        <Tag>{t("common:badge.federated")}</Tag>
        <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>
          {t("bind.readonlyHint")}
        </Typography.Paragraph>
        {object.federationRemotePath && (
          <Typography.Paragraph style={{ marginBottom: 0 }}>
            {t("bind.remotePath")}: <Typography.Text code>{object.federationRemotePath}</Typography.Text>
          </Typography.Paragraph>
        )}
        </Space>
      </section>
    );
  }

  const peers = peersQuery.data ?? [];
  const peerOptions = peers.filter((peer) => peer.enabled);
  const mirrorPeer = isMirror ? findPeerForCatalogMirrorPath(object.path, peers) : undefined;
  const mirrorRemoteSubtree =
    mirrorPeer && isMirror
      ? catalogMirrorToRemoteSubtree(object.path, mirrorPeer.pathPrefix ?? "root.platform")
      : null;

  return (
    <section className="panel panel-card federation-bind-panel">
      <Space orientation="vertical" size="middle" style={{ width: "100%" }}>
      <div>
        <Typography.Title level={3} style={{ margin: 0 }}>
          {t("bind.title")}
        </Typography.Title>
        {isBound && <Tag>{t("common:badge.federated")}</Tag>}
        <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>
          {t("bind.subtitle", { path: object.path })}
        </Typography.Paragraph>
      </div>

      {isMirror && (
        <div className="federation-bind-callout">{t("bind.mirrorCallout")}</div>
      )}

      {isMirror && mirrorPeer && mirrorRemoteSubtree && (
        <Space wrap>
          <Button onClick={() => setSyncSubtreeOpen(true)}>
            {t("subtreeSync.syncThisFolder")}
          </Button>
        </Space>
      )}

      {isMirror && !showPlaceLocally && (
        <Space wrap>
          <Button type="primary" onClick={() => setShowPlaceLocally(true)}>
            {t("bind.placeLocally")}
          </Button>
        </Space>
      )}

      {showPlaceLocally && (
        <Form
          layout="vertical"
          onFinish={() => {
            placeLocallyMutation.mutate();
          }}
        >
          <Space orientation="vertical" size="middle" style={{ width: "100%" }}>
            <Space size="middle" align="start" wrap>
              <Form.Item label={t("bind.parentPath")} style={{ minWidth: 260, marginBottom: 0 }}>
                <Input value={placeParentPath} onChange={(e) => setPlaceParentPath(e.target.value)} />
              </Form.Item>
              <Form.Item label={t("bind.nodeName")} style={{ minWidth: 220, marginBottom: 0 }}>
                <Input value={placeName} onChange={(e) => setPlaceName(e.target.value)} required />
              </Form.Item>
              <Form.Item label={t("bind.peer")} style={{ minWidth: 240, marginBottom: 0 }}>
                <Select
                  value={peerId}
                  onChange={setPeerId}
                  options={[
                    { value: "", label: t("common:empty.dash") },
                    ...peerOptions.map((peer) => ({
                      value: peer.id,
                      label: `${peer.name}${peer.tunnelConnected ? t("bind.tunnelSuffix") : ""}`,
                    })),
                  ]}
                />
              </Form.Item>
              <Form.Item label={t("bind.remotePathLabel")} style={{ minWidth: 360, marginBottom: 0 }}>
                <Input value={remotePath} onChange={(e) => setRemotePath(e.target.value)} required />
              </Form.Item>
            </Space>
            <Space wrap>
            <Button htmlType="submit" type="primary" disabled={placeLocallyMutation.isPending} loading={placeLocallyMutation.isPending}>
              {t("bind.createLocalBind")}
            </Button>
            <Button onClick={() => setShowPlaceLocally(false)}>
              {t("common:action.cancel")}
            </Button>
            </Space>
          </Space>
        </Form>
      )}

      {!showPlaceLocally && (
        <>
          <Form layout="vertical">
            <Space size="middle" align="start" wrap>
              <Form.Item label={t("bind.peer")} style={{ minWidth: 240, marginBottom: 0 }}>
                <Select
                  value={peerId}
                  onChange={setPeerId}
                  options={[
                    { value: "", label: t("bind.selectPeer") },
                    ...peerOptions.map((peer) => ({
                      value: peer.id,
                      label: `${peer.name}${peer.tunnelConnected ? t("bind.tunnelSuffix") : ""}`,
                    })),
                  ]}
                />
              </Form.Item>
              <Form.Item label={t("bind.remotePathLabel")} style={{ minWidth: 360, marginBottom: 0 }}>
                <Input
                  value={remotePath}
                  onChange={(e) => setRemotePath(e.target.value)}
                  placeholder={t("common:objectPath.placeholder")}
                />
              </Form.Item>
            </Space>
          </Form>

          <Space wrap>
            <Button
              disabled={!peerId || !remotePath.trim() || probeMutation.isPending}
              loading={probeMutation.isPending}
              onClick={() => probeMutation.mutate()}
            >
              {t("bind.probe")}
            </Button>
            {!isBound && !isMirror && (
              <Button
                type="primary"
                disabled={!peerId || !remotePath.trim() || bindMutation.isPending}
                loading={bindMutation.isPending}
                onClick={() => bindMutation.mutate()}
              >
                {t("bind.bind")}
              </Button>
            )}
            {isBound && (
              <>
                <Button
                  type="primary"
                  disabled={!peerId || !remotePath.trim() || rebindMutation.isPending}
                  loading={rebindMutation.isPending}
                  onClick={() => rebindMutation.mutate()}
                >
                  {t("bind.rebind")}
                </Button>
                {!showUnbindConfirm ? (
                  <Button
                    danger
                    disabled={unbindMutation.isPending}
                    onClick={() => setShowUnbindConfirm(true)}
                  >
                    {t("bind.unbind")}
                  </Button>
                ) : (
                  <Space className="federation-unbind-confirm" wrap>
                    <span>{t("bind.confirmUnbind", { name: object.displayName })}</span>
                    <Button
                      size="small"
                      danger
                      disabled={unbindMutation.isPending}
                      loading={unbindMutation.isPending}
                      onClick={() => unbindMutation.mutate()}
                    >
                      {t("bind.confirmUnbindAction")}
                    </Button>
                    <Button
                      size="small"
                      onClick={() => setShowUnbindConfirm(false)}
                    >
                      {t("common:action.cancel")}
                    </Button>
                  </Space>
                )}
              </>
            )}
          </Space>

          {probeResult && <pre className="mono federation-probe-result">{probeResult}</pre>}
        </>
      )}

      {error && <Alert type="error" showIcon message={error} />}
      {(bindMutation.isSuccess || rebindMutation.isSuccess || unbindMutation.isSuccess) && (
        <Alert type="success" showIcon message={t("bind.updated")} />
      )}
      {syncSubtreeOpen && mirrorPeer && mirrorRemoteSubtree && (
        <FederationCatalogSyncDialog
          peerId={mirrorPeer.id}
          peerName={mirrorPeer.name}
          remoteSubtreePath={mirrorRemoteSubtree}
          localParentPath={object.path}
          onClose={() => setSyncSubtreeOpen(false)}
          onSynced={() => {
            setSyncSubtreeOpen(false);
            invalidate();
          }}
          onError={(message) => {
            setSyncSubtreeOpen(false);
            setError(message);
          }}
        />
      )}
      </Space>
    </section>
  );
}
