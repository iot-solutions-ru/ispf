import { useState } from "react";
import { useTranslation } from "react-i18next";
import { useDraggableFab } from "../../ui/useDraggableFab";
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
  const drag = useDraggableFab(open);
  const moveHint = t("agent.move");
  const label = open ? t("agent.close") : t("agent.open");

  return (
    <>
      <button
        type="button"
        className={`operator-agent-fab${open ? " open" : ""}${drag.dragging ? " dragging" : ""}`}
        style={drag.buttonStyle}
        data-ispf-drag="1"
        aria-expanded={open}
        aria-label={`${label}. ${moveHint}`}
        title={`${label}. ${moveHint}`}
        onPointerDown={drag.onPointerDown}
        onPointerMove={drag.onPointerMove}
        onPointerUp={drag.onPointerUp}
        onPointerCancel={drag.onPointerCancel}
        onClick={(event) => {
          if (drag.consumeClick(event)) {
            return;
          }
          setOpen((prev) => !prev);
        }}
      >
        {open ? "×" : "AI"}
      </button>
      <OperatorAgentPanel
        appId={appId}
        open={open}
        onClose={() => setOpen(false)}
        onOpenDashboard={onOpenDashboard}
        onOpenReport={onOpenReport}
        panelRef={drag.drawerRef}
        style={drag.drawerStyle}
      />
    </>
  );
}
