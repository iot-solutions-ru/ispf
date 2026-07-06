import { useEffect, useMemo, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import SymbolBehaviorsEditor from "./SymbolBehaviorsEditor";
import type { MimicBindingSlot, MimicCustomSymbol, MimicElement, MimicSymbolBehavior } from "../../types/scadaMimic";
import { DEFAULT_CUSTOM_SVG_INNER, parseSvgUpload, sanitizeSvgMarkup } from "../../scada/customSvg";
import { convertElementToLibrarySymbol, convertPackToLibrarySymbol, isBuiltinSymbolId, isPackSymbolId } from "../../scada/convertBuiltinToLibrary";
import { createMimicId } from "../../scada/document";

interface CustomSvgEditorProps {
  element: MimicElement;
  customSymbols?: MimicCustomSymbol[];
  onUpdateElement: (element: MimicElement) => void;
  onAddCustomSymbol: (def: MimicCustomSymbol) => void;
  onUpdateCustomSymbol?: (id: string, patch: Partial<MimicCustomSymbol>) => void;
  onUpdateCustomSymbols?: (symbols: MimicCustomSymbol[]) => void;
}

function isCustomSvgElement(element: MimicElement): boolean {
  return element.symbolId === "custom.svg" || element.symbolId.startsWith("custom:");
}

function supportsSvgMarkupEditor(element: MimicElement): boolean {
  return isCustomSvgElement(element) || isBuiltinSymbolId(element.symbolId) || isPackSymbolId(element.symbolId);
}

function resolveLibraryDef(
  element: MimicElement,
  customSymbols?: MimicCustomSymbol[]
): MimicCustomSymbol | undefined {
  if (!element.symbolId.startsWith("custom:")) return undefined;
  const id = element.symbolId.slice("custom:".length);
  return customSymbols?.find((s) => s.id === id);
}

function resolveBaseSvg(element: MimicElement, libraryDef?: MimicCustomSymbol): string {
  const fromProps = element.props?.svg;
  if (typeof fromProps === "string" && fromProps.trim()) {
    return fromProps;
  }
  if (libraryDef?.svg?.trim()) {
    return libraryDef.svg;
  }
  return DEFAULT_CUSTOM_SVG_INNER;
}

export default function CustomSvgEditor({
  element,
  customSymbols,
  onUpdateElement,
  onAddCustomSymbol,
  onUpdateCustomSymbol,
  onUpdateCustomSymbols,
}: CustomSvgEditorProps) {
  const { t } = useTranslation("scada");
  const fileRef = useRef<HTMLInputElement>(null);
  const libraryDef = useMemo(
    () => resolveLibraryDef(element, customSymbols),
    [element.symbolId, customSymbols]
  );
  const baseSvg = useMemo(() => resolveBaseSvg(element, libraryDef), [element.props?.svg, libraryDef]);
  const [draftSvg, setDraftSvg] = useState(baseSvg);
  const [dirty, setDirty] = useState(false);

  useEffect(() => {
    setDraftSvg(baseSvg);
    setDirty(false);
  }, [element.id, baseSvg]);

  if (!supportsSvgMarkupEditor(element)) return null;

  if (isBuiltinSymbolId(element.symbolId) || isPackSymbolId(element.symbolId)) {
    const handleConvert = () => {
      const result = isPackSymbolId(element.symbolId)
        ? convertPackToLibrarySymbol(element, customSymbols ?? [])
        : convertElementToLibrarySymbol(element, customSymbols ?? []);
      if (result.customSymbols !== customSymbols) {
        onUpdateCustomSymbols?.(result.customSymbols);
      }
      onUpdateElement(result.element);
    };

    return (
      <div className="scada-props-section">
        <h3 className="scada-props-section-title">{t("props.customSvg")}</h3>
        <p className="scada-props-hint scada-props-hint-compact">{t("props.builtinSymbolHint")}</p>
        <button type="button" className="scada-btn-primary scada-btn-block" onClick={handleConvert}>
          {t("props.convertToCustomSvg")}
        </button>
      </div>
    );
  }

  if (!isCustomSvgElement(element)) return null;

  const width = Number(element.props?.width) || libraryDef?.width || 64;
  const height = Number(element.props?.height) || libraryDef?.height || 64;
  const viewBox = String(element.props?.viewBox ?? libraryDef?.viewBox ?? `0 0 ${width} ${height}`);
  const isLibraryRef = element.symbolId.startsWith("custom:");
  const hasInstanceOverride =
    isLibraryRef && typeof element.props?.svg === "string" && element.props.svg.trim().length > 0;
  const displayedSvg = dirty ? draftSvg : baseSvg;

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

  const handleApplyToLibrary = () => {
    if (!libraryDef || !onUpdateCustomSymbol) return;
    onUpdateCustomSymbol(libraryDef.id, {
      svg: sanitizeSvgMarkup(draftSvg),
      width,
      height,
      viewBox,
      inUserLibrary: true,
    });
    setDirty(false);
  };

  const handleResetOverride = () => {
    if (!element.props) return;
    const { svg: _svg, viewBox: _viewBox, ...rest } = element.props;
    onUpdateElement({ ...element, props: Object.keys(rest).length ? rest : undefined });
    setDirty(false);
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
      inUserLibrary: true,
      ...(Array.isArray(element.props?.behaviors)
        ? { behaviors: element.props.behaviors as MimicSymbolBehavior[] }
        : {}),
      ...(Array.isArray(element.props?.bindingSchema)
        ? { bindingSchema: element.props.bindingSchema as MimicBindingSlot[] }
        : {}),
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

  const handleBehaviorsChange = (patch: {
    behaviors: MimicSymbolBehavior[];
    bindingSchema: MimicBindingSlot[];
  }) => {
    if (isLibraryRef && libraryDef && onUpdateCustomSymbol) {
      onUpdateCustomSymbol(libraryDef.id, {
        behaviors: patch.behaviors,
        bindingSchema: patch.bindingSchema,
        inUserLibrary: true,
      });
      return;
    }
    if (element.symbolId === "custom.svg") {
      onUpdateElement({
        ...element,
        props: {
          ...element.props,
          behaviors: patch.behaviors,
          bindingSchema: patch.bindingSchema,
        },
      });
    }
  };

  const editorBehaviors = isLibraryRef
    ? libraryDef?.behaviors
    : ((Array.isArray(element.props?.behaviors) ? element.props.behaviors : undefined) as
        | MimicSymbolBehavior[]
        | undefined);
  const editorBindingSchema = isLibraryRef
    ? libraryDef?.bindingSchema
    : ((Array.isArray(element.props?.bindingSchema) ? element.props.bindingSchema : undefined) as
        | MimicBindingSlot[]
        | undefined);
  const canEditBehaviors =
    Boolean(isLibraryRef && libraryDef && onUpdateCustomSymbol) || element.symbolId === "custom.svg";

  return (
    <div className="scada-props-section">
      <h3 className="scada-props-section-title">{t("props.customSvg")}</h3>

      <div className="scada-custom-svg-preview">
        <svg viewBox={viewBox} width="100%" height="80" preserveAspectRatio="xMidYMid meet">
          <g dangerouslySetInnerHTML={{ __html: sanitizeSvgMarkup(displayedSvg) }} />
        </svg>
      </div>

      <label className="scada-form-field">
        <span className="scada-form-label">{t("props.customSvgMarkup")}</span>
        <textarea
          className="scada-form-input scada-svg-textarea mono"
          rows={8}
          spellCheck={false}
          value={displayedSvg}
          onChange={(e) => {
            setDraftSvg(e.target.value);
            setDirty(true);
          }}
        />
      </label>

      {dirty && (
        <div className="scada-form-row scada-form-row-stack">
          <button type="button" className="scada-btn-primary scada-btn-block" onClick={handleApplyDraft}>
            {isLibraryRef ? t("props.customSvgApplyInstance") : t("props.customSvgApply")}
          </button>
          {isLibraryRef && onUpdateCustomSymbol && libraryDef && (
            <button type="button" className="scada-btn-ghost scada-btn-block" onClick={handleApplyToLibrary}>
              {t("props.customSvgApplyLibrary")}
            </button>
          )}
        </div>
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

      {isLibraryRef && (
        <>
          <p className="scada-props-hint scada-props-hint-compact">
            {t("props.customSvgLibraryRef", {
              name: libraryDef?.name ?? element.symbolId,
            })}
          </p>
          <p className="scada-props-hint scada-props-hint-compact">
            {hasInstanceOverride
              ? t("props.customSvgInstanceOverrideActive")
              : t("props.customSvgInstanceOverrideHint")}
          </p>
          {hasInstanceOverride && (
            <button type="button" className="scada-btn-ghost scada-btn-block" onClick={handleResetOverride}>
              {t("props.customSvgResetOverride")}
            </button>
          )}
        </>
      )}

      {canEditBehaviors && (
        <SymbolBehaviorsEditor
          svg={displayedSvg}
          behaviors={editorBehaviors}
          bindingSchema={editorBindingSchema}
          onChange={handleBehaviorsChange}
        />
      )}
    </div>
  );
}

export { isCustomSvgElement, supportsSvgMarkupEditor };
