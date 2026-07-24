import { useTranslation } from "react-i18next";
import { Button, Modal } from "antd";
import DashboardBuilder from "./DashboardBuilder";
import type { DashboardSession } from "./DashboardContext";
import type { OpenDashboardOptions } from "./DashboardContext";

interface DashboardModalProps {
  path: string;
  title: string;
  session?: DashboardSession;
  selection?: Record<string, string>;
  params?: Record<string, unknown>;
  onSessionChange?: (next: DashboardSession) => void;
  onSelectionChange?: (next: Record<string, string>) => void;
  onParamsChange?: (next: Record<string, unknown>) => void;
  onNavigateDashboard?: (path: string, options?: OpenDashboardOptions) => void;
  onOpenDashboardModal?: (
    path: string,
    title?: string,
    options?: OpenDashboardOptions
  ) => void;
  onClose: () => void;
}

export default function DashboardModal({
  path,
  title,
  session,
  selection,
  params,
  onSessionChange,
  onSelectionChange,
  onParamsChange,
  onNavigateDashboard,
  onOpenDashboardModal,
  onClose,
}: DashboardModalProps) {
  const { t } = useTranslation(["dashboard", "common"]);

  return (
    <Modal
      title={title}
      open
      onCancel={onClose}
      footer={
        <Button onClick={onClose}>
          {t("common:action.close")}
        </Button>
      }
      width="min(96vw, 1400px)"
      className="dashboard-modal"
      destroyOnHidden
    >
      <div className="dashboard-modal-body">
        <DashboardBuilder
          path={path}
          operatorMode
          embeddedModal
          onClose={onClose}
          session={session}
          selection={selection}
          params={params}
          onSessionChange={onSessionChange}
          onSelectionChange={onSelectionChange}
          onParamsChange={onParamsChange}
          onNavigateDashboard={onNavigateDashboard}
          onOpenDashboardModal={onOpenDashboardModal}
        />
      </div>
    </Modal>
  );
}
