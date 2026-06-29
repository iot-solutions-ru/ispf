import { useEffect, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import type { MimicCustomSymbol, MimicElement } from "../../types/scadaMimic";
import { DEFAULT_CUSTOM_SVG_INNER, parseSvgUpload, sanitizeSvgMarkup } from "../../scada/customSvg";
import { createMimicId } from "../../scada/document";

interface CustomSvgEditorProps {
  element: MimicElement;
  customSymbols?: MimicCustomSymbol[];
  onUpdateElement: (element: MimicElement) => void;
  onAddCustomSymbol: (def: MimicCustomSymbol) => void;
}

function isCustomSvgElement(element: MimicElement): boolean {
  return element.symbolId === "custom.svg" || element.symbolId.startsWith("custom:");
}

export default function CustomSvgEditor({
  element,
  customSymbols,
  onUpdateElement,
  onAddCustomSymbol,
}: CustomSvgEditorProps) {
  const { t } = useTranslation("scada");
  const fileRef = useRef<HTMLInputElement>(null);
  const [draftSvg, setDraftSvg] = useState(String(element.props?.svg ?? DEFAULT_CUSTOM_SVG_INNER));
  const [dirty, setDirty] = useState(false);

  useEffect(() => {
    setDraftSvg(String(element.props?.svg ?? DEFAULT_CUSTOM_SVG_INNER));
    setDirty(false);
  }, [element.id, element.props?.svg]);

  if (!isCustomSvgElement(element)) return null;

  const width = Number(element.props?.width) || 64;
  const height = Number(element.props?.height) || 64;
  const viewBox = String(element.props?.viewBox ?? `0 0 ${width} ${height}`);

  const applySvg = (patch: Partial<{ svg: string; width: number; height: number; viewBox: string }>) => {
    const nextWidth = patch.width ?? width;
    const nextHeight = patch.height ?? height;
    const nextViewBox =
      patch.viewBox ??
      (patch.width != null || patch.height != null ? `0 0 ${nextWidth} ${nextHeight}` : viewBox);
    onUpdateElement({
      ...element,
      props: {
        ...element.props,
        ...patch,
        viewBox: nextViewBox,
      },
    });
    setDirty(false);
  };

  const handleApplyDraft = () => {
    applySvg({ svg: sanitizeSvgMarkup(draftSvg) });
  };

  const handleUpload = (file: File) => {
    const reader = new FileReader();
    reader.onload = () => {
      const parsed = parseSvgUpload(String(reader.result ?? ""));
      setDraftSvg(parsed.svg);
      applySvg(parsed);
    };
    reader.readAsText(file);
  };

  const handleSaveToLibrary = () => {
    const id = createMimicId("csym");
    const name =
      typeof element.props?.label === "string" && element.props.label.trim()
        ? element.props.label.trim()
        : t("props.customSvgDefaultName");
    const def: MimicCustomSymbol = {
      id,
      name,
      svg: sanitizeSvgMarkup(draftSvg),
      width,
      height,
      viewBox,
    };
    onAddCustomSymbol(def);
    onUpdateElement({
      ...element,
      symbolId: `custom:${id}`,
      props: {
        ...element.props,
        svg: def.svg,
        width: def.width,
        height: def.height,
        viewBox: def.viewBox,
      },
    });
  };

  return (
    <div className="scada-props-section">
      <h3 className="scada-props-section-title">{t("props.customSvg")}</h3>

      <div className="scada-custom-svg-preview">
        <svg viewBox={viewBox} width="100%" height="80" preserveAspectRatio="xMidYMid meet">
          <g dangerouslySetInnerHTML={{ __html: sanitizeSvgMarkup(dirty ? draftSvg : String(element.props?.svg ?? "")) }} />
        </svg>
      </div>

      <label className="scada-form-field">
        <span className="scada-form-label">{t("props.customSvgMarkup")}</span>
        <textarea
          className="scada-form-input scada-svg-textarea mono"
          rows={8}
          spellCheck={false}
          value={dirty ? draftSvg : String(element.props?.svg ?? "")}
          onChange={(e) => {
            setDraftSvg(e.target.value);
            setDirty(true);
          }}
        />
      </label>

      {dirty && (
        <button type="button" className="scada-btn-primary scada-btn-block" onClick={handleApplyDraft}>
          {t("props.customSvgApply")}
        </button>
      )}

      <div className="scada-form-row">
        <label className="scada-form-field scada-form-field-half">
          <span className="scada-form-label">{t("props.customSvgWidth")}</span>
          <input
            type="number"
            className="scada-form-input"
            min={8}
            value={width}
            onChange={(e) => applySvg({ width: Number(e.target.value) })}
          />
        </label>
        <label className="scada-form-field scada-form-field-half">
          <span className="scada-form-label">{t("props.customSvgHeight")}</span>
          <input
            type="number"
            className="scada-form-input"
            min={8}
            value={height}
            onChange={(e) => applySvg({ height: Number(e.target.value) })}
          />
        </label>
      </div>

      <input
        ref={fileRef}
        type="file"
        accept=".svg,image/svg+xml"
        className="scada-file-input-hidden"
        onChange={(e) => {
          const file = e.target.files?.[0];
          if (file) handleUpload(file);
          e.target.value = "";
        }}
      />
      <button type="button" className="scada-btn-ghost scada-btn-block" onClick={() => fileRef.current?.click()}>
        {t("props.customSvgUpload")}
      </button>

      {element.symbolId === "custom.svg" && (
        <button type="button" className="scada-btn-ghost scada-btn-block" onClick={handleSaveToLibrary}>
          {t("props.customSvgSaveLibrary")}
        </button>
      )}

      {element.symbolId.startsWith("custom:") && (
        <p className="scada-props-hint scada-props-hint-compact">
          {t("props.customSvgLibraryRef", {
            name: customSymbols?.find((s) => s.id === element.symbolId.slice("custom:".length))?.name ?? element.symbolId,
          })}
        </p>
      )}
    </div>
  );
}

export { isCustomSvgElement };
