import { Select, Typography } from "antd";
import { useTranslation } from "react-i18next";
import type { SecurityRoleSummary } from "../../api/securityRoles";

interface RoleMultiSelectProps {
  id: string;
  label: string;
  roles: SecurityRoleSummary[];
  selected: string[];
  onChange: (roles: string[]) => void;
  disabled?: boolean;
}

export default function RoleMultiSelect({
  id,
  label,
  roles,
  selected,
  onChange,
  disabled = false,
}: RoleMultiSelectProps) {
  const { t } = useTranslation("inspector");

  return (
    <div className="full" style={{ marginBottom: 12 }}>
      <Typography.Text strong style={{ display: "block", marginBottom: 6 }}>
        {label}
      </Typography.Text>
      <Select
        id={id}
        mode="multiple"
        style={{ width: "100%" }}
        disabled={disabled}
        value={selected}
        onChange={onChange}
        placeholder={roles.length === 0 ? t("variables.rolesEmpty") : undefined}
        options={roles.map((role) => ({
          value: role.name,
          label: role.displayName !== role.name ? `${role.name} — ${role.displayName}` : role.name,
        }))}
        optionFilterProp="label"
      />
    </div>
  );
}
