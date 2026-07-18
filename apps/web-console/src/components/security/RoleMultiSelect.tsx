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

  function toggle(roleName: string) {
    if (disabled) {
      return;
    }
    if (selected.includes(roleName)) {
      onChange(selected.filter((name) => name !== roleName));
      return;
    }
    onChange([...selected, roleName]);
  }

  return (
    <fieldset className="function-form-multiselect variable-role-picker full" disabled={disabled}>
      <legend>{label}</legend>
      {roles.length === 0 ? (
        <p className="hint">{t("variables.rolesEmpty")}</p>
      ) : (
        <div className="function-form-multiselect-options">
          {roles.map((role) => (
            <label key={role.name} className="function-form-multiselect-option">
              <input
                type="checkbox"
                id={`${id}-${role.name}`}
                checked={selected.includes(role.name)}
                onChange={() => toggle(role.name)}
              />
              <span>
                <code>{role.name}</code>
                {role.displayName !== role.name ? ` — ${role.displayName}` : ""}
              </span>
            </label>
          ))}
        </div>
      )}
    </fieldset>
  );
}
