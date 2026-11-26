import { useEffect, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { formatAdminFocusChip } from "../../context/adminFocusUtils";
import { useAdminFocusOptional } from "../../context/useAdminFocus";
import ModalPortal from "../../ui/ModalPortal";
import { useDraggableFab } from "../../ui/useDraggableFab";
import AdminCopilotPanel from "./AdminCopilotPanel";
import { installCopilotFocusGuard } from "./copilotFocusGuard";

export default function AdminCopilotFab() {
  const { t } = useTranslation("ai");
  const [open, setOpen] = useState(false);
  const focusRegistry = useAdminFocusOptional();
  const lastToken = useRef(0);
  const openRequestToken = focusRegistry?.copilotOpenToken ?? 0;

  useEffect(() => {
    if (openRequestToken > 0 && openRequestToken !== lastToken.current) {
      lastToken.current = openRequestToken;
      setOpen(true);
    }
  }, [openRequestToken]);

  useEffect(() => {
    if (!open) {
      return;
    }
    return installCopilotFocusGuard();
  }, [open]);

  const chip = formatAdminFocusChip(focusRegistry?.focus ?? null, focusRegistry?.focusTrail);
  const drag = useDraggableFab(open);
  const moveHint = t("copilot.move");
  const label = open
    ? t("copilot.close")
    : chip
      ? `${t("copilot.open")} — ${chip}`
      : t("copilot.open");

  return (
    <ModalPortal>
      <button
        type="button"
        className={`admin-copilot-fab${open ? " open" : ""}${drag.dragging ? " dragging" : ""}`}
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
      <AdminCopilotPanel
        open={open}
        onClose={() => setOpen(false)}
        panelRef={drag.drawerRef}
        style={drag.drawerStyle}
      />
    </ModalPortal>
  );
}
