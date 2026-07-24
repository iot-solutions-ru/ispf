import { useQuery } from "@tanstack/react-query";
import { Modal, Space, Typography } from "antd";
import { fetchVariables } from "../../api";
import { historizableFieldsFromVariable } from "../../utils/object/variableHistoryFields";
import VariableHistoryPanel from "./VariableHistoryPanel";

interface VariableHistoryModalProps {
  objectPath: string;
  variableName: string;
  valueField?: string;
  title?: string;
  onClose: () => void;
}

export default function VariableHistoryModal({
  objectPath,
  variableName,
  valueField,
  title,
  onClose,
}: VariableHistoryModalProps) {
  const variablesQuery = useQuery({
    queryKey: ["variables", objectPath],
    queryFn: () => fetchVariables(objectPath),
    enabled: Boolean(objectPath),
  });

  const variable = variablesQuery.data?.find((item) => item.name === variableName);
  const fields = variable
    ? historizableFieldsFromVariable(variable)
    : [valueField ?? "value"];

  return (
    <Modal
      title={title ?? variableName}
      open
      onCancel={onClose}
      footer={null}
      destroyOnHidden
      width={960}
      className="modal-variable-history"
    >
      <Space orientation="vertical" size="middle" style={{ width: "100%" }}>
        <Typography.Paragraph type="secondary" className="modal-variable-history-path" style={{ marginBottom: 0 }}>
          <Typography.Text code>{objectPath}</Typography.Text> · <Typography.Text code>{variableName}</Typography.Text>
        </Typography.Paragraph>
        <VariableHistoryPanel
          objectPath={objectPath}
          variableName={variableName}
          fields={fields}
          refreshIntervalMs={30_000}
        />
      </Space>
    </Modal>
  );
}
