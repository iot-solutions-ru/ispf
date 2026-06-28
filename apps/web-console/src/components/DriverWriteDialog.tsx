import { useTranslation } from "react-i18next";
import DriverWriteForm from "./DriverWriteForm";

interface DriverWriteDialogProps {
  devicePath: string;
  canManage: boolean;
  onClose: () => void;
}

export default function DriverWriteDialog({ devicePath, canManage, onClose }: DriverWriteDialogProps) {
  const { t } = useTranslation(["inspector", "common"]);

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal modal-wide" onClick={(event) => event.stopPropagation()}>
        <header className="modal-head">
          <h3>{t("inspector:driver.write.dialogTitle")}</h3>
          <button type="button" className="btn small" onClick={onClose}>×</button>
        </header>
        <div className="modal-body">
          <p className="hint">
            <code>{devicePath}</code>
          </p>
          <DriverWriteForm devicePath={devicePath} canManage={canManage} />
        </div>
        <footer className="modal-foot">
          <button type="button" className="btn" onClick={onClose}>{t("common:action.close")}</button>
        </footer>
      </div>
    </div>
  );
}
