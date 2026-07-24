import { Alert, Button, Space } from "antd";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import {
  dryRunAiDeploy,
  generateAiBundle,
  validateAiBundle,
} from "../../api/ai";
import { fetchDeployHistory, rollbackDeploy } from "../../api/applications";
import { importPackage } from "../../api/packages";
import { useAgentChat } from "../../context/AgentChatContext";

const DEFAULT_MANIFEST = `{
  "version": "1.0.0",
  "displayName": "AI generated app",
  "schemaName": "app_ai_generated",
  "migrations": []
}`;

interface AiStudioBundleTabProps {
  appId: string;
  setAppId: (value: string) => void;
  prompt: string;
  setPrompt: (value: string) => void;
  manifestText: string;
  setManifestText: (value: string) => void;
  validationText: string | null;
  setValidationText: (value: string | null) => void;
  dryRunText: string | null;
  setDryRunText: (value: string | null) => void;
}

export function defaultBundleManifest(): string {
  return DEFAULT_MANIFEST;
}

export default function AiStudioBundleTab({
  appId,
  setAppId,
  prompt,
  setPrompt,
  manifestText,
  setManifestText,
  validationText,
  setValidationText,
  dryRunText,
  setDryRunText,
}: AiStudioBundleTabProps) {
  const { t } = useTranslation("ai");
  const { provider } = useAgentChat();
  const queryClient = useQueryClient();

  const historyQuery = useQuery({
    queryKey: ["deploy-history", appId],
    queryFn: () => fetchDeployHistory(appId.trim()),
    enabled: Boolean(appId.trim()),
  });

  const rollbackMutation = useMutation({
    mutationFn: (version: string) => rollbackDeploy(appId.trim(), version),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["deploy-history", appId] });
      queryClient.invalidateQueries({ queryKey: ["objects"] });
    },
  });

  const validateMutation = useMutation({
    mutationFn: () => {
      const manifest = JSON.parse(manifestText) as unknown;
      return validateAiBundle(appId.trim(), manifest);
    },
    onSuccess: (data) => setValidationText(JSON.stringify(data, null, 2)),
  });

  const dryRunMutation = useMutation({
    mutationFn: () => {
      const manifest = JSON.parse(manifestText) as unknown;
      return dryRunAiDeploy(appId.trim(), manifest);
    },
    onSuccess: (data) => setDryRunText(JSON.stringify(data, null, 2)),
  });

  const generateMutation = useMutation({
    mutationFn: () => generateAiBundle(appId.trim(), prompt),
    onSuccess: (data) => {
      const artifact = data.artifact as Record<string, unknown> | undefined;
      if (data.publishable && artifact && Object.keys(artifact).length > 0) {
        setManifestText(JSON.stringify(artifact, null, 2));
      }
      setValidationText(JSON.stringify(data.validation, null, 2));
      setDryRunText(JSON.stringify(data.dryRun, null, 2));
    },
  });

  const publishMutation = useMutation({
    mutationFn: () => {
      const manifest = JSON.parse(manifestText) as unknown;
      return importPackage(appId.trim(), manifest);
    },
  });

  const publishErrorText = publishMutation.error
    ? String(publishMutation.error)
    : null;
  const publishNeedsSignedLicense = Boolean(
    publishErrorText && /signed license|require-signed-bundles|403/i.test(publishErrorText)
  );

  const previewUrl = appId.trim()
    ? `/?mode=operator&app=${encodeURIComponent(appId.trim())}`
    : "/?mode=operator";

  return (
    <div className="panel-card">
      <div className="form-grid">
        <label>
          {t("bundle.appId")}
          <input value={appId} onChange={(e) => setAppId(e.target.value)} />
        </label>

        <label className="full">
          {t("bundle.prompt")}
          <textarea
            className="code-field mono"
            rows={4}
            value={prompt}
            onChange={(e) => setPrompt(e.target.value)}
            spellCheck={false}
          />
        </label>
      </div>

      <Space className="form-actions" wrap>
        <Button
          type="primary"
          disabled={!provider?.available}
          loading={generateMutation.isPending}
          onClick={() => generateMutation.mutate()}
        >
          {generateMutation.isPending ? t("bundle.generating") : t("bundle.generate")}
        </Button>
        <Button
          loading={validateMutation.isPending}
          onClick={() => validateMutation.mutate()}
        >
          {t("bundle.validate")}
        </Button>
        <Button
          loading={dryRunMutation.isPending}
          onClick={() => dryRunMutation.mutate()}
        >
          {t("bundle.dryRun")}
        </Button>
        <Button
          type="primary"
          disabled={!appId.trim()}
          loading={publishMutation.isPending}
          onClick={() => publishMutation.mutate()}
        >
          {t("bundle.publish")}
        </Button>
        <Button href={previewUrl} target="_blank" rel="noreferrer">
          {t("bundle.operatorPreview")}
        </Button>
      </Space>

      {generateMutation.data && !generateMutation.data.publishable && (
        <Alert type="error" showIcon message={t("bundle.notPublishable")} />
      )}

      {(generateMutation.error || validateMutation.error || dryRunMutation.error || publishMutation.error) && (
        <Alert
          type="error"
          showIcon
          message={String(
            generateMutation.error
              || validateMutation.error
              || dryRunMutation.error
              || publishMutation.error
          )}
          description={
            publishNeedsSignedLicense ? t("bundle.signedLicenseHint") : undefined
          }
        />
      )}

      {publishMutation.data && (
        <pre className="mono small">{JSON.stringify(publishMutation.data, null, 2)}</pre>
      )}

      <section className="ai-studio-history">
        <h4>{t("bundle.deployHistory")}</h4>
        {historyQuery.isLoading && <p className="op-muted">{t("bundle.loadingHistory")}</p>}
        {historyQuery.data && historyQuery.data.length === 0 && (
          <p className="op-muted">{t("bundle.noDeployHistory")}</p>
        )}
        {historyQuery.data && historyQuery.data.length > 0 && (
          <ul className="deploy-history-list">
            {historyQuery.data.map((entry) => (
              <li key={entry.version}>
                <span>
                  v{entry.version}
                  {entry.active ? t("bundle.active") : ""}
                  {entry.deployedAt ? ` — ${entry.deployedAt}` : ""}
                </span>
                <Button
                  size="small"
                  disabled={entry.active}
                  loading={rollbackMutation.isPending}
                  onClick={() => rollbackMutation.mutate(entry.version)}
                >
                  {t("bundle.rollback")}
                </Button>
              </li>
            ))}
          </ul>
        )}
        {rollbackMutation.error && (
          <Alert type="error" showIcon message={String(rollbackMutation.error)} />
        )}
      </section>

      <div className="form-grid">
        <label className="full">
          {t("bundle.manifestJson")}
          <div className="ai-studio-bundle-file-row">
            <input
              type="file"
              accept="application/json,.json"
              data-testid="bundle-manifest-file"
              onChange={async (event) => {
                const file = event.target.files?.[0];
                if (!file) return;
                try {
                  const text = await file.text();
                  const parsed = JSON.parse(text) as Record<string, unknown>;
                  setManifestText(JSON.stringify(parsed, null, 2));
                  setValidationText(null);
                  setDryRunText(null);
                  const op =
                    (parsed.operatorUi as { appId?: string } | undefined)?.appId
                    || (parsed.operatorManifest as { appId?: string } | undefined)?.appId;
                  if (!appId.trim() && typeof op === "string" && op.trim()) {
                    setAppId(op.trim());
                  }
                } catch (err) {
                  setValidationText(String(err));
                } finally {
                  event.target.value = "";
                }
              }}
            />
            <span className="op-muted">{t("bundle.loadFromFileHint")}</span>
          </div>
          <textarea
            className="json-editor"
            rows={16}
            value={manifestText}
            onChange={(e) => setManifestText(e.target.value)}
            spellCheck={false}
          />
        </label>

        {validationText && (
          <label className="full">
            {t("bundle.validationResult")}
            <textarea className="code-field mono" rows={8} readOnly value={validationText} />
          </label>
        )}

        {dryRunText && (
          <label className="full">
            {t("bundle.dryRun")}
            <textarea className="code-field mono" rows={8} readOnly value={dryRunText} />
          </label>
        )}
      </div>
    </div>
  );
}
