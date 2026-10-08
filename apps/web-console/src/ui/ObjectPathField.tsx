import { useState } from "react";
import { useTranslation } from "react-i18next";
import { Button, Input, Select, Tooltip } from "antd";
import type { ObjectType } from "../types";
import ObjectTreePickerDialog from "./ObjectTreePickerDialog";

export interface ObjectPathOption {
  path: string;
  displayName: string;
}

export interface ObjectPathFieldProps {
  id?: string;
  label?: string;
  value: string;
  onChange: (path: string) => void;
  objects?: ObjectPathOption[];
  filterTypes?: ObjectType[];
  rootPath?: string;
  placeholder?: string;
  /** Shown on the caption. The input itself stays empty. */
  hint?: string;
  /** Seconds before the caption tooltip appears. */
  hintDelay?: number;
  disabled?: boolean;
  allowManual?: boolean;
  className?: string;
  pickerTitle?: string;
}

export default function ObjectPathField({
  id,
  label,
  value,
  onChange,
  objects,
  filterTypes,
  rootPath,
  placeholder,
  hint,
  hintDelay,
  disabled = false,
  allowManual = true,
  className = "",
  pickerTitle,
}: ObjectPathFieldProps) {
  const { t } = useTranslation("common");
  const [pickerOpen, setPickerOpen] = useState(false);

  return (
    <>
      <label className={`object-path-field ${className}`.trim()} htmlFor={id}>
        {label &&
          (hint ? (
            <Tooltip title={hint} mouseEnterDelay={hintDelay ?? 0.1}>
              <span className="field-caption field-caption-hint">{label}</span>
            </Tooltip>
          ) : (
            <span className="field-caption">{label}</span>
          ))}
        <div className="object-path-field-controls">
          {objects && objects.length > 0 && (
            <Select
              value={value}
              disabled={disabled}
              onChange={onChange}
              options={[
                { value: "", label: "—" },
                ...objects.map((obj) => ({ value: obj.path, label: obj.displayName })),
              ]}
            />
          )}
          {allowManual && (
            <Input
              id={id}
              type="text"
              value={value}
              disabled={disabled}
              placeholder={placeholder ?? t("objectPath.placeholder")}
              onChange={(event) => onChange(event.target.value)}
            />
          )}
          <Button
            size="small"
            className="object-path-browse"
            disabled={disabled}
            title={t("objectPath.browseTree")}
            aria-label={t("objectPath.browseTree")}
            onClick={() => setPickerOpen(true)}
          >
            …
          </Button>
        </div>
      </label>
      <ObjectTreePickerDialog
        open={pickerOpen}
        title={pickerTitle}
        onClose={() => setPickerOpen(false)}
        onSelect={onChange}
        filterTypes={filterTypes}
        rootPath={rootPath}
      />
    </>
  );
}
