import { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Button, Form, Input, Space, Typography } from "antd";
import { importPackage } from "../../api/packages";
import BundleLicenseInfoPanel from "./BundleLicenseInfoPanel";
import BundleLicenseErrorAlert from "./BundleLicenseErrorAlert";

interface PackageImportPanelProps {
  defaultPackageId?: string;
}

export default function PackageImportPanel({ defaultPackageId = "demo" }: PackageImportPanelProps) {
  const { t } = useTranslation("platform");
  const queryClient = useQueryClient();
  const [packageId, setPackageId] = useState(defaultPackageId);
  const [manifestText, setManifestText] = useState(`{
  "version": "1.0.0",
  "displayName": "Demo package",
  "schemaName": "app_demo",
  "migrations": []
}`);

  const parsedManifest = useMemo(() => {
    try {
      return JSON.parse(manifestText) as unknown;
    } catch {
      return null;
    }
  }, [manifestText]);

  const importMutation = useMutation({
    mutationFn: () => {
      const manifest = JSON.parse(manifestText) as unknown;
      return importPackage(packageId.trim(), manifest);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["objects"] });
    },
  });

  return (
    <div className="package-import-panel">
      <Space orientation="vertical" style={{ width: "100%" }}>
        <Typography.Title level={3}>{t("packageImport.title")}</Typography.Title>
        <Typography.Paragraph type="secondary">{t("packageImport.subtitle")}</Typography.Paragraph>
        <BundleLicenseInfoPanel
          appId={packageId.trim() || undefined}
          manifest={parsedManifest ?? undefined}
          compact
        />
        <Form layout="vertical">
          <Form.Item label="packageId">
            <Input value={packageId} onChange={(e) => setPackageId(e.target.value)} />
          </Form.Item>
          <Form.Item label="manifest JSON" className="full">
            <Input.TextArea
              className="mono"
              rows={12}
              value={manifestText}
              onChange={(e) => setManifestText(e.target.value)}
              spellCheck={false}
            />
          </Form.Item>
        </Form>
        <Space className="form-actions">
          <Button
            type="primary"
            disabled={importMutation.isPending || !packageId.trim()}
            onClick={() => importMutation.mutate()}
          >
            {t("packageImport.import")}
          </Button>
        </Space>
      </Space>
      {importMutation.error && <BundleLicenseErrorAlert error={importMutation.error} />}
      {importMutation.data && (
        <Input.TextArea
          className="mono small"
          value={JSON.stringify(importMutation.data, null, 2)}
          readOnly
          autoSize={{ minRows: 4 }}
        />
      )}
    </div>
  );
}
