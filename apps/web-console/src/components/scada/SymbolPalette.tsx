import { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { SYMBOL_CATEGORIES, listAllSymbols, listSymbolsByCategory } from "../../scada/symbols/registry";
import SymbolPreview from "./SymbolPreview";

interface SymbolPaletteProps {
  selectedSymbolId: string | null;
  onSelectSymbol: (symbolId: string) => void;
}

export default function SymbolPalette({ selectedSymbolId, onSelectSymbol }: SymbolPaletteProps) {
  const { t } = useTranslation("scada");
  const [search, setSearch] = useState("");
  const [category, setCategory] = useState<string>("process");

  const symbols = useMemo(() => {
    const q = search.trim().toLowerCase();
    const base = q ? listAllSymbols() : listSymbolsByCategory(category);
    if (!q) return base;
    return base.filter((s) => t(s.nameKey).toLowerCase().includes(q) || s.id.includes(q));
  }, [category, search, t]);

  const categoryCounts = useMemo(() => {
    const counts: Record<string, number> = {};
    for (const cat of SYMBOL_CATEGORIES) {
      counts[cat] = listSymbolsByCategory(cat).length;
    }
    return counts;
  }, []);

  return (
    <div className="scada-palette">
      <input
        type="search"
        placeholder={t("palette.search")}
        value={search}
        onChange={(e) => setSearch(e.target.value)}
      />
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
          {SYMBOL_CATEGORIES.map((cat) => (
            <option key={cat} value={cat}>
              {t(`categories.${cat}`)} ({categoryCounts[cat] ?? 0})
            </option>
          ))}
        </select>
      </label>
      <div className="scada-palette-list-header">
        {search.trim()
          ? t("palette.searchResults", { count: symbols.length })
          : t("palette.categoryCount", { count: symbols.length })}
      </div>
      <div className="scada-palette-list">
        {symbols.map((sym) => (
          <button
            key={sym.id}
            type="button"
            className={`scada-palette-item${selectedSymbolId === sym.id ? " active" : ""}`}
            onClick={() => onSelectSymbol(sym.id)}
            title={sym.id}
          >
            <SymbolPreview symbol={sym} />
            <span className="scada-palette-item-text">
              <span className="scada-palette-item-id">{t(sym.nameKey)}</span>
              <span className="scada-palette-item-meta">{sym.defaultWidth}×{sym.defaultHeight}</span>
            </span>
          </button>
        ))}
      </div>
    </div>
  );
}
