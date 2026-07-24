import { useState } from "react";
import { useTranslation } from "react-i18next";
import { useMutation } from "@tanstack/react-query";
import { Alert, Button, Form, Input, Select, Space, Typography } from "antd";
import {
  downloadBrickExport,
  downloadHaystackExport,
  exportHaystackModel,
} from "../../api/semanticExport";
import { ObjectPathField } from "../../ui";

const PREVIEW_MAX_CHARS = 4000;

export default function SemanticExportPanel() {
  const { t } = useTranslation("system");
  const [rootPath, setRootPath] = useState("root.platform.devices");
  const [includePoints, setIncludePoints] = useState(true);
  const [preview, setPreview] = useState<string | null>(null);
  const [previewTruncated, setPreviewTruncated] = useState(false);

  const previewMutation = useMutation({
    mutationFn: () => exportHaystackModel({ rootPath, includePoints }),
    onSuccess: (data) => {
      const text = JSON.stringify(data, null, 2);
      setPreviewTruncated(text.length > PREVIEW_MAX_CHARS);
      setPreview(text.slice(0, PREVIEW_MAX_CHARS));
    },
  });

  const haystackMutation = useMutation({
    mutationFn: () => downloadHaystackExport({ rootPath, includePoints }),
  });

  const brickJsonMutation = useMutation({
    mutationFn: () => downloadBrickExport("jsonld", { rootPath, includePoints }),
  });

  const brickTurtleMutation = useMutation({
    mutationFn: () => downloadBrickExport("turtle", { rootPath, includePoints }),
  });

  const error =
    haystackMutation.error
    ?? brickJsonMutation.error
    ?? brickTurtleMutation.error
    ?? previewMutation.error;

  return (
    <section className="system-panel semantic-export-panel">
      <header>
        <Typography.Title level={3}>{t("semanticExport.title")}</Typography.Title>
        <Typography.Paragraph type="secondary">{t("semanticExport.subtitle")}</Typography.Paragraph>
      </header>

      <Form layout="vertical" className="semantic-export-options">
        <ObjectPathField
          className="full"
          label={t("semanticExport.rootPath")}
          value={rootPath}
          onChange={setRootPath}
          placeholder="root.platform.devices"
        />
        <Form.Item label={t("semanticExport.includePoints")}>
          <Select
            value={includePoints ? "true" : "false"}
            onChange={(value) => setIncludePoints(value === "true")}
            options={[
              { value: "true", label: t("semanticExport.includePoints") },
              { value: "false", label: "—" },
            ]}
          />
        </Form.Item>
      </Form>

      <Space orientation="vertical" className="semantic-export-actions">
        <Space className="form-actions semantic-export-preview-actions">
          <Button
            disabled={previewMutation.isPending}
            onClick={() => previewMutation.mutate()}
          >
            {previewMutation.isPending ? t("semanticExport.previewing") : t("semanticExport.preview")}
          </Button>
        </Space>
        <Space className="form-actions semantic-export-download-actions">
          <Button
            type="primary"
            disabled={haystackMutation.isPending}
            onClick={() => haystackMutation.mutate()}
          >
            {haystackMutation.isPending ? t("semanticExport.exporting") : t("semanticExport.haystackJson")}
          </Button>
          <Button
            disabled={brickJsonMutation.isPending}
            onClick={() => brickJsonMutation.mutate()}
          >
            {brickJsonMutation.isPending ? t("semanticExport.exporting") : t("semanticExport.brickJsonLd")}
          </Button>
          <Button
            disabled={brickTurtleMutation.isPending}
            onClick={() => brickTurtleMutation.mutate()}
          >
            {brickTurtleMutation.isPending ? t("semanticExport.exporting") : t("semanticExport.brickTurtle")}
          </Button>
        </Space>
      </Space>

      {error && <Alert type="error" showIcon message={String(error)} />}
      {(haystackMutation.isSuccess || brickJsonMutation.isSuccess || brickTurtleMutation.isSuccess) && (
        <Alert type="success" showIcon message={t("semanticExport.downloadStarted")} />
      )}

      {preview && (
        <div className="semantic-export-preview-block panel-card">
          <Typography.Title level={4}>{t("semanticExport.preview")}</Typography.Title>
          {previewTruncated && (
            <Typography.Paragraph type="secondary">{t("semanticExport.previewTruncated")}</Typography.Paragraph>
          )}
          <Input.TextArea
            className="semantic-export-preview mono small"
            value={preview}
            readOnly
            autoSize={{ minRows: 6 }}
          />
        </div>
      )}
    </section>
  );
}
