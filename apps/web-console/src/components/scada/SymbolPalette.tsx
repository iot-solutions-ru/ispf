import { useVirtualizer } from "@tanstack/react-virtual";
import { useEffect, useMemo, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import type { MimicCustomSymbol } from "../../types/scadaMimic";
import {
  SYMBOL_CATEGORIES,
  ensurePackLoaded,
  listAllSymbols,
  listDocumentCustomSymbols,
  listSymbolsByCategory,
  type RegisteredSymbol,
} from "../../scada/symbols/registry";
import { packSymbolLabel, packSymbolMatchesSearch } from "../../scada/symbols/symbolPackLoader";
import { IconSearch } from "./ScadaEditorIcons";
import SymbolPreview from "./SymbolPreview";

const PALETTE_CATEGORIES = [...SYMBOL_CATEGORIES, "custom"] as const;
const ITEM_HEIGHT = 52;

interface SymbolPaletteProps {
  selectedSymbolId: string | null;
  customSymbols?: MimicCustomSymbol[];
  onSelectSymbol: (symbolId: string) => void;
  onUploadCustomSymbol?: (file: File) => void;
}

function symbolLabel(sym: RegisteredSymbol, t: (key: string) => string, locale: string): string {
  if (sym.id.startsWith("pack.ispf-pid.")) {
    return packSymbolLabel(sym, locale);
  }
  return sym.displayName ?? t(sym.nameKey);
}

function symbolMatchesSearch(sym: RegisteredSymbol, q: string, t: (key: string) => string, locale: string): boolean {
  if (sym.id.startsWith("pack.ispf-pid.")) {
    return packSymbolMatchesSearch(sym, q);
  }
  const label = symbolLabel(sym, t, locale).toLowerCase();
  return label.includes(q) || sym.id.toLowerCase().includes(q);
}

export default function SymbolPalette({
  selectedSymbolId,
  customSymbols,
  onSelectSymbol,
  onUploadCustomSymbol,
}: SymbolPaletteProps) {
  const { t, i18n } = useTranslation("scada");
  const locale = i18n.language;
  const fileRef = useRef<HTMLInputElement>(null);
  const listRef = useRef<HTMLDivElement>(null);
  const [search, setSearch] = useState("");
  const [category, setCategory] = useState<string>("pack-tanks");
  const [packReady, setPackReady] = useState(false);

  useEffect(() => {
    let cancelled = false;
    ensurePackLoaded().then(() => {
      if (!cancelled) setPackReady(true);
    });
    return () => {
      cancelled = true;
    };
  }, []);

  const documentCustom = useMemo(() => listDocumentCustomSymbols(customSymbols), [customSymbols]);

  const symbols = useMemo(() => {
    const q = search.trim().toLowerCase();
    if (category === "custom") {
      if (!q) return documentCustom;
      return documentCustom.filter((s) => symbolMatchesSearch(s, q, t, locale));
    }
    const base = q ? listAllSymbols() : listSymbolsByCategory(category);
    if (!q) return base;
    return base.filter((s) => symbolMatchesSearch(s, q, t, locale));
  }, [category, search, t, locale, documentCustom, packReady]);

  const categoryCounts = useMemo(() => {
    const counts: Record<string, number> = { custom: documentCustom.length };
    for (const cat of SYMBOL_CATEGORIES) {
      counts[cat] = listSymbolsByCategory(cat).length;
    }
    return counts;
  }, [documentCustom.length, packReady]);

  const totalCount = listAllSymbols().length + documentCustom.length;

  const virtualizer = useVirtualizer({
    count: symbols.length,
    getScrollElement: () => listRef.current,
    estimateSize: () => ITEM_HEIGHT,
    overscan: 10,
  });

  return (
    <div className="scada-palette">
      <div className="scada-panel-header">
        <h2 className="scada-panel-title">{t("palette.library")}</h2>
        <span className="scada-panel-badge">{totalCount}</span>
      </div>

      <div className="scada-palette-body">
        <div className="scada-palette-search-wrap">
          <IconSearch className="scada-palette-search-icon" />
          <input
            type="search"
            className="scada-palette-search"
            placeholder={t("palette.search")}
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
        </div>

        <label className="scada-palette-category-select">
          <span className="scada-palette-category-label">{t("palette.category")}</span>
          <select
            value={category}
            onChange={(e) => {
              setCategory(e.target.value);
              setSearch("");
            }}
            disabled={Boolean(search.trim())}
          >
            {PALETTE_CATEGORIES.map((cat) => (
              <option key={cat} value={cat}>
                {t(`categories.${cat}`)} ({categoryCounts[cat] ?? 0})
              </option>
            ))}
          </select>
        </label>

        {category === "custom" && onUploadCustomSymbol && (
          <>
            <input
              ref={fileRef}
              type="file"
              accept=".svg,image/svg+xml"
              className="scada-file-input-hidden"
              onChange={(e) => {
                const file = e.target.files?.[0];
                if (file) onUploadCustomSymbol(file);
                e.target.value = "";
              }}
            />
            <button
              type="button"
              className="scada-btn-primary scada-btn-block scada-palette-upload"
              onClick={() => fileRef.current?.click()}
            >
              {t("palette.uploadSvg")}
            </button>
          </>
        )}

        <div className="scada-palette-list-header">
          {search.trim()
            ? t("palette.searchResults", { count: symbols.length })
            : t("palette.categoryCount", { count: symbols.length })}
        </div>

        <div ref={listRef} className="scada-palette-list">
          {symbols.length === 0 ? (
            <p className="scada-palette-empty">
              {category === "custom" ? t("palette.customEmpty") : t("palette.empty")}
            </p>
          ) : (
            <div
              className="scada-palette-virtual-inner"
              style={{ height: virtualizer.getTotalSize(), position: "relative" }}
            >
              {virtualizer.getVirtualItems().map((item) => {
                const sym = symbols[item.index]!;
                return (
                  <button
                    key={sym.id}
                    type="button"
                    className={`scada-palette-item${selectedSymbolId === sym.id ? " active" : ""}`}
                    style={{
                      position: "absolute",
                      top: 0,
                      left: 0,
                      width: "100%",
                      height: `${item.size}px`,
                      transform: `translateY(${item.start}px)`,
                    }}
                    onClick={() => onSelectSymbol(sym.id)}
                    title={sym.id}
                  >
                    <SymbolPreview symbol={sym} />
                    <span className="scada-palette-item-text">
                      <span className="scada-palette-item-id">{symbolLabel(sym, t, locale)}</span>
                      <span className="scada-palette-item-meta">
                        {sym.defaultWidth}×{sym.defaultHeight}
                      </span>
                    </span>
                  </button>
                );
              })}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
