import { useMutation } from "@tanstack/react-query";
import { Alert, Form, Input, Modal } from "antd";
import { useEffect } from "react";
import { useTranslation } from "react-i18next";
import { createSecurityRole } from "../../api/securityRoles";
import { isTechnicalIdentifier } from "../../utils/ui/technicalIdentifier";

interface CreateSecurityRoleDialogProps {
  open: boolean;
  onClose: () => void;
  onCreated: (objectPath: string) => void;
}

interface CreateRoleFormValues {
  name: string;
  displayName?: string;
  description?: string;
}

export default function CreateSecurityRoleDialog({
  open,
  onClose,
  onCreated,
}: CreateSecurityRoleDialogProps) {
  const { t } = useTranslation(["security", "common"]);
  const [form] = Form.useForm<CreateRoleFormValues>();

  useEffect(() => {
    if (!open) {
      form.resetFields();
    }
  }, [open, form]);

  const mutation = useMutation({
    mutationFn: (values: CreateRoleFormValues) =>
      createSecurityRole({
        name: values.name.trim(),
        displayName: values.displayName?.trim() || values.name.trim(),
        description: values.description?.trim() ?? "",
      }),
    onSuccess: (created) => {
      onCreated(created.objectPath);
      onClose();
    },
  });

  return (
    <Modal
      title={t("createRole.title")}
      open={open}
      onCancel={onClose}
      okText={t("common:action.create")}
      cancelText={t("common:action.cancel")}
      confirmLoading={mutation.isPending}
      destroyOnHidden
      onOk={() => form.submit()}
    >
      <Form<CreateRoleFormValues>
        form={form}
        layout="vertical"
        requiredMark="optional"
        onFinish={(values) => mutation.mutate(values)}
      >
        <Form.Item
          name="name"
          label={t("createRole.name")}
          rules={[
            { required: true, message: t("createRole.name") },
            {
              validator: async (_, value: string) => {
                if (!value || isTechnicalIdentifier(value, "securityName")) {
                  return;
                }
                throw new Error(t("common:error.invalidNamedIdentifier"));
              },
            },
          ]}
        >
          <Input placeholder="supervisor" autoFocus />
        </Form.Item>

        <Form.Item name="displayName" label={t("common:field.displayName")}>
          <Input placeholder="Supervisor" />
        </Form.Item>

        <Form.Item name="description" label={t("common:field.description")}>
          <Input.TextArea rows={3} placeholder={t("createRole.descriptionPlaceholder")} />
        </Form.Item>

        {mutation.error && (
          <Alert type="error" showIcon message={String(mutation.error)} style={{ marginBottom: 12 }} />
        )}
      </Form>
    </Modal>
  );
}
