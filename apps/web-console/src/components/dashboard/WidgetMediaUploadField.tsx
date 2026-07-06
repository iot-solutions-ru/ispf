import { useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import {
  isWidgetMediaDataUrl,
  MAX_WIDGET_MEDIA_BYTES,
  readFileAsDataUrl,
  resolveWidgetMediaSrc,
} from "./widgetMediaUrl";

interface WidgetMediaUploadFieldProps {
  label: string;
  value: string;
  onChange: (url: string) => void;
  accept: string;
  placeholder?: string;
  previewAlt?: string;
}

export default function WidgetMediaUploadField({
  label,
  value,
  onChange,
  accept,
  placeholder,
  previewAlt,
}: WidgetMediaUploadFieldProps) {
  const { t } = useTranslation("widgets");
  const fileRef = useRef<HTMLInputElement>(null);
  const [error, setError] = useState<string | null>(null);

  const previewSrc = resolveWidgetMediaSrc(value);
  const embedded = isWidgetMediaDataUrl(value);

  const handleFile = async (file: File) => {
    setError(null);
    if (file.size > MAX_WIDGET_MEDIA_BYTES) {
      setError(
        t("editor.mediaTooLarge", {
          maxMb: Math.round(MAX_WIDGET_MEDIA_BYTES / (1024 * 1024)),
        })
      );
      return;
    }
    try {
      const dataUrl = await readFileAsDataUrl(file);
      onChange(dataUrl);
    } catch {
      setError(t("editor.mediaReadFailed"));
    }
  };

  return (
    <div className="widget-media-upload-field">
      <label>
        <span className="field-caption">{label}</span>
        <input
          type="text"
          value={value}
          placeholder={placeholder}
          onChange={(e) => {
            setError(null);
            onChange(e.target.value);
          }}
        />
      </label>

      <div className="widget-media-upload-actions">
        <input
          ref={fileRef}
          type="file"
          accept={accept}
          className="widget-media-file-input"
          onChange={(e) => {
            const file = e.target.files?.[0];
            if (file) void handleFile(file);
            e.target.value = "";
          }}
        />
        <button type="button" className="btn small" onClick={() => fileRef.current?.click()}>
          {t("editor.mediaUpload")}
        </button>
        {embedded && (
          <button type="button" className="btn small" onClick={() => onChange("")}>
            {t("editor.mediaClear")}
          </button>
        )}
      </div>

      {embedded && <p className="hint widget-media-upload-hint">{t("editor.mediaEmbeddedHint")}</p>}
      {error && <p className="hint widget-media-upload-error">{error}</p>}

      {previewSrc && (
        <div className="widget-media-upload-preview">
          <img src={previewSrc} alt={previewAlt ?? label} />
        </div>
      )}
    </div>
  );
}
