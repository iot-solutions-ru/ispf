import { useTranslation } from "react-i18next";
import { Button, Modal, Space, Typography } from "antd";
import DriverWriteForm from "./DriverWriteForm";

interface DriverWriteDialogProps {
  devicePath: string;
  canManage: boolean;
  onClose: () => void;
}

export default function DriverWriteDialog({ devicePath, canManage, onClose }: DriverWriteDialogProps) {
  const { t } = useTranslation(["inspector", "common"]);

  return (
    <Modal
      title={t("inspector:driver.write.dialogTitle")}
      open
      onCancel={onClose}
      destroyOnHidden
      width={760}
      footer={
        <Button onClick={onClose}>
          {t("common:action.close")}
        </Button>
      }
    >
      <Space orientation="vertical" size="middle" style={{ width: "100%" }}>
        <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>
          <Typography.Text code>{devicePath}</Typography.Text>
        </Typography.Paragraph>
        <DriverWriteForm devicePath={devicePath} canManage={canManage} />
      </Space>
    </Modal>
  );
}
