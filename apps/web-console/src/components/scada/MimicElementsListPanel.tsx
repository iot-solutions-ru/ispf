import { useMemo } from "react";
import { useTranslation } from "react-i18next";
import type { MimicElement, MimicLayer } from "../../types/scadaMimic";
import { getSymbol } from "../../scada/symbols/registry";

interface MimicElementsListPanelProps {
  elements: MimicElement[];
  layers: MimicLayer[];
  customSymbolNames?: Map<string, string>;
  selectedIds: Set<string>;
  onSelectElement: (id: string, additive: boolean) => void;
}

function elementLabel(el: MimicElement, locale: string, customSymbolNames?: Map<string, string>): string {
  if (el.symbolId.startsWith("custom:")) {
    const customId = el.symbolId.slice("custom:".length);
    return customSymbolNames?.get(customId) ?? el.symbolId;
  }
  const sym = getSymbol(el.symbolId);
  if (!sym) {
    const slug = el.symbolId.split(".").pop() ?? el.symbolId;
    return slug.replace(/-/g, " ");
  }
  if (sym.id.startsWith("pack.ispf-pid.")) {
    const props = sym.paletteProps as { nameRu?: string } | undefined;
    if (locale.startsWith("ru") && props?.nameRu) {
      return props.nameRu;
    }
    return sym.displayName ?? sym.id;
  }
  return sym.displayName ?? el.symbolId;
}

export default function MimicElementsListPanel({
  elements,
  layers,
  customSymbolNames,
  selectedIds,
  onSelectElement,
}: MimicElementsListPanelProps) {
  const { t, i18n } = useTranslation("scada");
  const locale = i18n.language;

  const grouped = useMemo(() => {
    const byLayer = new Map<string, { element: MimicElement; zIndex: number }[]>();
    elements.forEach((element, index) => {
      const list = byLayer.get(element.layerId) ?? [];
      list.push({ element, zIndex: index });
      byLayer.set(element.layerId, list);
    });
    return layers
      .filter((layer) => byLayer.has(layer.id))
      .map((layer) => ({
        layer,
        items: [...(byLayer.get(layer.id) ?? [])].reverse(),
      }));
  }, [elements, layers]);

  const totalCount = elements.length;

  return (
    <div className="scada-elements-list-panel">
      <div className="scada-panel-header scada-panel-header-compact">
        <h2 className="scada-panel-title">{t("elementsList.title")}</h2>
        <span className="scada-elements-list-count" title={t("elementsList.countHint")}>
          {totalCount}
        </span>
      </div>
      {totalCount === 0 ? (
        <p className="scada-elements-list-empty">{t("elementsList.empty")}</p>
      ) : (
        <div className="scada-elements-list-groups">
          {grouped.map(({ layer, items }) => (
            <section key={layer.id} className="scada-elements-list-group">
              <h3 className="scada-elements-list-group-title">
                {layer.name}
                {!layer.visible ? (
                  <span className="scada-elements-list-hidden" title={t("elementsList.layerHidden")}>
                    ·
                  </span>
                ) : null}
              </h3>
              <ul className="scada-elements-list">
                {items.map(({ element, zIndex }) => {
                  const selected = selectedIds.has(element.id);
                  return (
                    <li key={element.id}>
                      <button
                        type="button"
                        className={`scada-elements-list-item${selected ? " selected" : ""}`}
                        onClick={(event) => onSelectElement(element.id, event.shiftKey || event.ctrlKey || event.metaKey)}
                        title={t("elementsList.selectHint", { z: zIndex + 1 })}
                      >
                        <span className="scada-elements-list-z" aria-hidden>
                          {zIndex + 1}
                        </span>
                        <span className="scada-elements-list-label">
                          {elementLabel(element, locale, customSymbolNames)}
                        </span>
                      </button>
                    </li>
                  );
                })}
              </ul>
            </section>
          ))}
        </div>
      )}
    </div>
  );
}
