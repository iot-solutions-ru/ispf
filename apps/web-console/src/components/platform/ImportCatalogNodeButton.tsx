import { useRef, useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { Alert, Button, Input, Modal, Typography } from "antd";
import { importCatalogNode } from "../../api/platformBackup";
import { requestObjectTreeInvalidate } from "../../hooks/useLazyObjectTree";
import { isTechnicalIdentifier } from "../../utils/ui/technicalIdentifier";

interface ImportCatalogNodeButtonProps {
  parentPath: string;
  onCreated: (path: string) => void;
}

export default function ImportCatalogNodeButton({ parentPath, onCreated }: ImportCatalogNodeButtonProps) {
  const { t } = useTranslation(["explorer", "common"]);
  const queryClient = useQueryClient();
  const fileInputRef = useRef<HTMLInputElement | null>(null);
  const [open, setOpen] = useState(false);
  const [name, setName] = useState("");
  const [fileText, setFileText] = useState("");
  const [fileLabel, setFileLabel] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const close = () => {
    if (submitting) return;
    setOpen(false);
    setError(null);
  };

  const submit = async () => {
    const trimmed = name.trim();
    if (!isTechnicalIdentifier(trimmed, "pathSegment")) {
      setError(t("common:error.invalidPathSegment"));
      return;
    }
    if (!fileText.trim()) {
      setError(t("folderList.importJsonNeedFile"));
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      const path = await importCatalogNode(parentPath, trimmed, fileText);
      await queryClient.invalidateQueries({ queryKey: ["objects", parentPath] });
      requestObjectTreeInvalidate();
      onCreated(path);
      setOpen(false);
      setName("");
      setFileText("");
      setFileLabel("");
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <>
      <Button onClick={() => setOpen(true)}>{t("folderList.importJson")}</Button>
      <Modal
        open={open}
        title={t("folderList.importJsonTitle")}
        okText={t("folderList.importJsonSubmit")}
        cancelText={t("common:action.cancel")}
        confirmLoading={submitting}
        onCancel={close}
        onOk={() => void submit()}
        destroyOnHidden
      >
        <Typography.Paragraph type="secondary">{t("folderList.importJsonHint")}</Typography.Paragraph>
        {error && <Alert type="error" showIcon title={error} style={{ marginBottom: 12 }} />}
        <label htmlFor="catalog-import-name">
          <Typography.Text>{t("folderList.importJsonName")}</Typography.Text>
        </label>
        <Input
          id="catalog-import-name"
          value={name}
          onChange={(event) => setName(event.target.value)}
          autoComplete="off"
          style={{ marginBottom: 12 }}
        />
        <div>
          <Button onClick={() => fileInputRef.current?.click()}>{t("folderList.importJsonFile")}</Button>
          <input
            ref={fileInputRef}
            type="file"
            accept="application/json,.json"
            hidden
            onChange={(event) => {
              const file = event.target.files?.[0];
              event.target.value = "";
              if (!file) {
                setFileText("");
                setFileLabel("");
                return;
              }
              setFileLabel(file.name);
              void file.text().then(setFileText).catch((err: unknown) => {
                setFileText("");
                setError(err instanceof Error ? err.message : String(err));
              });
            }}
          />
        </div>
        {fileLabel && (
          <Typography.Text type="secondary" style={{ display: "block", marginTop: 8 }}>
            {fileLabel}
          </Typography.Text>
        )}
      </Modal>
    </>
  );
}
