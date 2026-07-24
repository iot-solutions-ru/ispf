import { useMutation } from "@tanstack/react-query";
import { Button, Modal, Select, Space, Table, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import {
  previewFederationCatalogSync,
  previewFederationSubtreeSync,
  syncFederationCatalog,
  syncFederationSubtree,
  type CatalogSyncConflict,
  type CatalogSyncResolution,
  type CatalogSyncResolutionAction,
} from "../../api/federation";

interface FederationCatalogSyncDialogProps {
  peerId: string;
  peerName: string;
  /** When set, sync only this remote subtree instead of full catalog. */
  remoteSubtreePath?: string;
  localParentPath?: string;
  onClose: () => void;
  onSynced: (message: string) => void;
  onError: (message: string) => void;
}

export default function FederationCatalogSyncDialog({
  peerId,
  peerName,
  remoteSubtreePath,
  localParentPath,
  onClose,
  onSynced,
  onError,
}: FederationCatalogSyncDialogProps) {
  const { t } = useTranslation(["federation", "common"]);
  const subtreeMode = Boolean(remoteSubtreePath?.trim());
  const [preview, setPreview] = useState<Awaited<ReturnType<typeof previewFederationCatalogSync>> | null>(null);
  const [actions, setActions] = useState<Record<string, CatalogSyncResolutionAction>>({});

  const previewMutation = useMutation({
    mutationFn: () =>
      subtreeMode
        ? previewFederationSubtreeSync(peerId, {
            remoteSubtreePath: remoteSubtreePath!,
            localParentPath,
          })
        : previewFederationCatalogSync(peerId),
    onSuccess: (data) => {
      setPreview(data);
      const defaults: Record<string, CatalogSyncResolutionAction> = {};
      for (const conflict of data.conflicts) {
        defaults[conflict.localPath] = "SKIP";
      }
      setActions(defaults);
    },
    onError: (error: Error) => onError(error.message),
  });

  const syncMutation = useMutation({
    mutationFn: (resolutions: CatalogSyncResolution[]) =>
      subtreeMode
        ? syncFederationSubtree(peerId, {
            remoteSubtreePath: remoteSubtreePath!,
            localParentPath,
            resolutions,
          })
        : syncFederationCatalog(peerId, resolutions),
    onSuccess: (result) => {
      onSynced(
        t("peers.syncResult", {
          localRoot: result.localRoot,
          created: result.created,
          updated: result.updated,
          remoteCount: result.remoteCount,
          skipped: result.skipped,
        })
      );
      onClose();
    },
    onError: (error: Error) => onError(error.message),
  });

  useEffect(() => {
    previewMutation.mutate();
  }, [peerId, remoteSubtreePath, localParentPath]);

  const conflictRows = preview?.conflicts ?? [];
  const titleKey = subtreeMode ? "subtreeSync.title" : "catalogSync.title";
  const applyKey = subtreeMode ? "subtreeSync.apply" : "catalogSync.apply";
  const conflictColumns: ColumnsType<CatalogSyncConflict> = [
    {
      title: t("catalogSync.column.localPath"),
      dataIndex: "localPath",
      key: "localPath",
      render: (_value, conflict) => (
        <Space orientation="vertical" size={0}>
          <Typography.Text code>{conflict.localPath}</Typography.Text>
          <Typography.Text type="secondary">{conflict.localDisplayName}</Typography.Text>
        </Space>
      ),
    },
    {
      title: t("catalogSync.column.type"),
      dataIndex: "type",
      key: "type",
      render: (type: CatalogSyncConflict["type"]) => t(`catalogSync.conflict.${type}`),
    },
    {
      title: t("catalogSync.column.action"),
      key: "action",
      render: (_value, conflict) => (
        <Select<CatalogSyncResolutionAction>
          value={actions[conflict.localPath] ?? "SKIP"}
          onChange={(value) =>
            setActions((current) => ({
              ...current,
              [conflict.localPath]: value,
            }))
          }
          options={[
            { value: "SKIP", label: t("catalogSync.action.skip") },
            { value: "BIND", label: t("catalogSync.action.bind") },
          ]}
          style={{ minWidth: 140 }}
        />
      ),
    },
  ];

  return (
    <Modal
      title={t(titleKey, { peer: peerName, remotePath: remoteSubtreePath })}
      open
      onCancel={onClose}
      destroyOnHidden
      width={760}
      footer={[
        <Button key="cancel" onClick={onClose}>
          {t("common:action.cancel")}
        </Button>,
        <Button
          key="apply"
          type="primary"
          disabled={!preview}
          loading={syncMutation.isPending}
          onClick={() => {
            const resolutions = conflictRows.map((conflict) => ({
              localPath: conflict.localPath,
              action: actions[conflict.localPath] ?? "SKIP",
            }));
            syncMutation.mutate(resolutions);
          }}
        >
          {t(applyKey)}
        </Button>,
      ]}
    >
      <Space orientation="vertical" size="middle" style={{ width: "100%" }}>
        {previewMutation.isPending && <Typography.Text type="secondary">{t("catalogSync.loading")}</Typography.Text>}
        {preview && (
          <>
            {subtreeMode && (
              <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>
                <Typography.Text code>{remoteSubtreePath}</Typography.Text>
                {localParentPath ? (
                  <>
                    {" → "}
                    <Typography.Text code>{localParentPath}</Typography.Text>
                  </>
                ) : null}
              </Typography.Paragraph>
            )}
            <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>
              {t("catalogSync.summary", {
                localRoot: preview.localRoot,
                createCount: preview.createCount,
                updateCount: preview.updateCount,
                remoteCount: preview.remoteCount,
                conflictCount: preview.conflicts.length,
              })}
            </Typography.Paragraph>
            {conflictRows.length > 0 ? (
              <Table<CatalogSyncConflict>
                size="small"
                rowKey="localPath"
                columns={conflictColumns}
                dataSource={conflictRows}
                pagination={false}
              />
            ) : (
              <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>
                {t("catalogSync.noConflicts")}
              </Typography.Paragraph>
            )}
          </>
        )}
      </Space>
    </Modal>
  );
}
