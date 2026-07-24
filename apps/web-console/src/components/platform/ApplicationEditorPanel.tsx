import { useQuery } from "@tanstack/react-query";
import { Alert } from "antd";
import { useTranslation } from "react-i18next";
import { fetchObjectEditor } from "../../api";
import ApplicationDeployPanel from "./ApplicationDeployPanel";
import { resolveApplicationAppId } from "../../utils/platform/applicationPath";
import PlatformSqlEditorShell from "./PlatformSqlEditorShell";

interface ApplicationEditorPanelProps {
  path: string;
  title?: string;
  onClose: () => void;
  onOpenProperties?: () => void;
  canManage?: boolean;
}

export default function ApplicationEditorPanel({
  path,
  title,
  onClose,
  onOpenProperties,
  canManage = false,
}: ApplicationEditorPanelProps) {
  const { t } = useTranslation(["platform", "inspector"]);
  const editorQuery = useQuery({
    queryKey: ["object-editor", path],
    queryFn: () => fetchObjectEditor(path),
  });

  const ctx = editorQuery.data?.object;
  const appId = ctx ? resolveApplicationAppId(path, ctx.description) : null;
  const displayTitle = title ?? ctx?.displayName ?? path.split(".").pop() ?? path;

  if (editorQuery.isLoading) {
    return <p className="hint">{t("platform:application.loading")}</p>;
  }

  if (editorQuery.error) {
    return <Alert type="error" showIcon message={String(editorQuery.error)} />;
  }

  return (
    <PlatformSqlEditorShell
      title={displayTitle}
      subtitle={t("platform:application.explorerSubtitle")}
      path={path}
      onClose={onClose}
      onOpenProperties={onOpenProperties}
    >
      {appId ? (
        <ApplicationDeployPanel
          appId={appId}
          displayName={displayTitle}
          canManage={canManage}
        />
      ) : (
        <p className="hint">{t("inspector:deploy.appIdMissing")}</p>
      )}
    </PlatformSqlEditorShell>
  );
}
