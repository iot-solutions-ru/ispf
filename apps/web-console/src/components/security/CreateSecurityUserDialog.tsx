import { useMutation, useQuery } from "@tanstack/react-query";
import { Alert, Form, Input, Modal, Select } from "antd";
import { useEffect } from "react";
import { useTranslation } from "react-i18next";
import { createSecurityUser } from "../../api/securityUsers";
import { fetchSecurityRoles } from "../../api/securityRoles";
import { isTechnicalIdentifier } from "../../utils/ui/technicalIdentifier";
import { localizedRoleDescription } from "../../utils/security/localizedRoleDescription";

interface CreateSecurityUserDialogProps {
  open: boolean;
  onClose: () => void;
  onCreated: (objectPath: string) => void;
}

interface CreateUserFormValues {
  username: string;
  displayName?: string;
  password: string;
  role: string;
}

export default function CreateSecurityUserDialog({
  open,
  onClose,
  onCreated,
}: CreateSecurityUserDialogProps) {
  const { t } = useTranslation(["security", "common"]);
  const [form] = Form.useForm<CreateUserFormValues>();

  const rolesQuery = useQuery({
    queryKey: ["security-roles"],
    queryFn: fetchSecurityRoles,
    enabled: open,
  });

  const availableRoles = rolesQuery.data ?? [];
  const defaultRole = availableRoles[0]?.name ?? "operator";

  useEffect(() => {
    if (!open) {
      form.resetFields();
      return;
    }
    form.setFieldsValue({ role: defaultRole });
  }, [open, defaultRole, form]);

  const mutation = useMutation({
    mutationFn: (values: CreateUserFormValues) =>
      createSecurityUser({
        username: values.username.trim(),
        displayName: values.displayName?.trim() || values.username.trim(),
        password: values.password,
        roles: [values.role],
      }),
    onSuccess: (created) => {
      onCreated(created.objectPath);
      onClose();
    },
  });

  return (
    <Modal
      title={t("createUser.title")}
      open={open}
      onCancel={onClose}
      okText={t("common:action.create")}
      cancelText={t("common:action.cancel")}
      confirmLoading={mutation.isPending}
      destroyOnHidden
      onOk={() => form.submit()}
    >
      <Form<CreateUserFormValues>
        form={form}
        layout="vertical"
        requiredMark="optional"
        initialValues={{ role: "operator" }}
        onFinish={(values) => mutation.mutate(values)}
      >
        <Form.Item
          name="username"
          label={t("users.column.login")}
          rules={[
            { required: true, message: t("users.column.login") },
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
          <Input placeholder="operator2" autoFocus />
        </Form.Item>

        <Form.Item name="displayName" label={t("common:field.displayName")}>
          <Input placeholder="Operator 2" />
        </Form.Item>

        <Form.Item
          name="password"
          label={t("createUser.password")}
          rules={[
            { required: true, message: t("createUser.password") },
            { min: 4, message: t("createUser.password") },
          ]}
        >
          <Input.Password />
        </Form.Item>

        <Form.Item
          name="role"
          label={t("user.role")}
          rules={[{ required: true, message: t("user.role") }]}
        >
          <Select
            loading={rolesQuery.isLoading}
            disabled={availableRoles.length === 0}
            options={availableRoles.map((item) => {
              const desc = localizedRoleDescription(t, item.name, item.description);
              return {
                value: item.name,
                label: desc ? `${item.name} — ${desc}` : item.name,
              };
            })}
          />
        </Form.Item>

        {mutation.error && (
          <Alert type="error" showIcon message={String(mutation.error)} style={{ marginBottom: 12 }} />
        )}
      </Form>
    </Modal>
  );
}
