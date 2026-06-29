import { useMemo, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import type { MimicCustomSymbol } from "../../types/scadaMimic";
import {
  SYMBOL_CATEGORIES,
  listAllSymbols,
  listDocumentCustomSymbols,
  listSymbolsByCategory,
  type RegisteredSymbol,
} from "../../scada/symbols/registry";
import { IconSearch } from "./ScadaEditorIcons";
import SymbolPreview from "./SymbolPreview";

const PALETTE_CATEGORIES = [...SYMBOL_CATEGORIES, "custom"] as const;

interface SymbolPaletteProps {
  selectedSymbolId: string | null;
  customSymbols?: MimicCustomSymbol[];
  onSelectSymbol: (symbolId: string) => void;
  onUploadCustomSymbol?: (file: File) => void;
}

function symbolLabel(sym: RegisteredSymbol, t: (key: string) => string): string {
  return sym.displayName ?? t(sym.nameKey);
}

export default function SymbolPalette({
  selectedSymbolId,
  customSymbols,
  onSelectSymbol,
  onUploadCustomSymbol,
}: SymbolPaletteProps) {
  const { t } = useTranslation("scada");
  const fileRef = useRef<HTMLInputElement>(null);
  const [search, setSearch] = useState("");
  const [category, setCategory] = useState<string>("process");

  const documentCustom = useMemo(() => listDocumentCustomSymbols(customSymbols), [customSymbols]);

  const symbols = useMemo(() => {
    const q = search.trim().toLowerCase();
    if (category === "custom") {
      if (!q) return documentCustom;
      return documentCustom.filter(
        (s) => symbolLabel(s, t).toLowerCase().includes(q) || s.id.toLowerCase().includes(q)
      );
    }
    const base = q ? listAllSymbols() : listSymbolsByCategory(category);
    if (!q) return base;
    return base.filter(
      (s) => symbolLabel(s, t).toLowerCase().includes(q) || s.id.toLowerCase().includes(q)
    );
  }, [category, search, t, documentCustom]);

  const categoryCounts = useMemo(() => {
    const counts: Record<string, number> = { custom: documentCustom.length };
    for (const cat of SYMBOL_CATEGORIES) {
      counts[cat] = listSymbolsByCategory(cat).length;
    }
    return counts;
  }, [documentCustom.length]);

  const totalCount = listAllSymbols().length + documentCustom.length;

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

        <div className="scada-palette-list">
          {symbols.length === 0 ? (
            <p className="scada-palette-empty">
              {category === "custom" ? t("palette.customEmpty") : t("palette.empty")}
            </p>
          ) : (
            symbols.map((sym) => (
              <button
                key={sym.id}
                type="button"
                className={`scada-palette-item${selectedSymbolId === sym.id ? " active" : ""}`}
                onClick={() => onSelectSymbol(sym.id)}
                title={sym.id}
              >
                <SymbolPreview symbol={sym} />
                <span className="scada-palette-item-text">
                  <span className="scada-palette-item-id">{symbolLabel(sym, t)}</span>
                  <span className="scada-palette-item-meta">
                    {sym.defaultWidth}×{sym.defaultHeight}
                  </span>
                </span>
              </button>
            ))
          )}
        </div>
      </div>
    </div>
  );
}
