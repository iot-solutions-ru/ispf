import { useState } from "react";
import { useTranslation } from "react-i18next";
import OperatorAgentPanel from "./OperatorAgentPanel";

interface OperatorAgentFabProps {
  appId: string;
  onOpenDashboard?: (path: string) => void;
  onOpenReport?: (path: string) => void;
}

export default function OperatorAgentFab({
  appId,
  onOpenDashboard,
  onOpenReport,
}: OperatorAgentFabProps) {
  const { t } = useTranslation("operator");
  const [open, setOpen] = useState(false);

  return (
    <>
      <button
        type="button"
        className={`operator-agent-fab${open ? " open" : ""}`}
        aria-expanded={open}
        aria-label={t("agent.open")}
        title={t("agent.open")}
        onClick={() => setOpen((prev) => !prev)}
      >
        {open ? "×" : "AI"}
      </button>
      <OperatorAgentPanel
        appId={appId}
        open={open}
        onClose={() => setOpen(false)}
        onOpenDashboard={onOpenDashboard}
        onOpenReport={onOpenReport}
      />
    </>
  );
}
