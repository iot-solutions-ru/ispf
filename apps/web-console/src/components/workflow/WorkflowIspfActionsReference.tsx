import { useTranslation } from "react-i18next";
import { Alert, Space, Tag, Typography } from "antd";
import { WORKFLOW_ISPF_ACTIONS } from "../../types/automation";

export default function WorkflowIspfActionsReference() {
  const { t } = useTranslation("workflow");

  return (
    <section className="workflow-ispf-reference">
      <Space direction="vertical" size="small" style={{ width: "100%" }}>
        <Typography.Title level={4} style={{ margin: 0 }}>
          {t("ispfReference.title")}
        </Typography.Title>
        <Alert type="info" showIcon title={t("ispfReference.hint")} />
      </Space>
      <ul className="workflow-ispf-action-list">
        {WORKFLOW_ISPF_ACTIONS.map((entry) => (
          <li key={entry.action}>
            <Space direction="vertical" size={2}>
              <Space size="small" wrap>
                <Tag color="blue">{entry.action}</Tag>
                <Typography.Text>{entry.label}</Typography.Text>
              </Space>
              <Typography.Text type="secondary">{entry.attrs.join(", ")}</Typography.Text>
            </Space>
          </li>
        ))}
      </ul>
    </section>
  );
}
