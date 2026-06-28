import { useTranslation } from "react-i18next";

interface OperatorSidebarToggleProps {
  open: boolean;
  onClick: () => void;
}

export default function OperatorSidebarToggle({ open, onClick }: OperatorSidebarToggleProps) {
  const { t } = useTranslation("operator");

  return (
    <button
      type="button"
      className="btn operator-sidebar-toggle"
      aria-expanded={open}
      aria-controls="operator-sidebar-panel"
      onClick={onClick}
    >
      {open ? t("sidebar.close") : t("sidebar.open")}
    </button>
  );
}
