import { useTranslation } from "react-i18next";
import Modal from "../../ui/Modal";
import AnalyticsTagInspector from "./AnalyticsTagInspector";

interface AnalyticsTagInspectorModalProps {
  open: boolean;
  tagPath: string | null;
  onClose: () => void;
}

export default function AnalyticsTagInspectorModal({
  open,
  tagPath,
  onClose,
}: AnalyticsTagInspectorModalProps) {
  const { t } = useTranslation("inspector");

  return (
    <Modal
      open={open && Boolean(tagPath)}
      title={t("computations.inspectTitle")}
      onClose={onClose}
      wide
      className="analytics-tag-inspector-modal"
    >
      {tagPath && <AnalyticsTagInspector path={tagPath} readOnly />}
    </Modal>
  );
}
