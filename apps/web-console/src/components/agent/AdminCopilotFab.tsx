import { useEffect, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { formatAdminFocusChip, useAdminFocusOptional } from "../../context/AdminFocusContext";
import ModalPortal from "../../ui/ModalPortal";
import AdminCopilotPanel from "./AdminCopilotPanel";

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

  const chip = formatAdminFocusChip(focusRegistry?.focus ?? null, focusRegistry?.focusTrail);

  return (
    <ModalPortal>
      <button
        type="button"
        className={`admin-copilot-fab${open ? " open" : ""}`}
        aria-expanded={open}
        aria-label={t("copilot.open")}
        title={chip ? `${t("copilot.open")} — ${chip}` : t("copilot.open")}
        onClick={() => setOpen((prev) => !prev)}
      >
        {open ? "×" : "AI"}
      </button>
      <AdminCopilotPanel open={open} onClose={() => setOpen(false)} />
    </ModalPortal>
  );
}
