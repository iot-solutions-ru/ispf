import { useTranslation } from "react-i18next";
import type { MimicLayer } from "../../types/scadaMimic";
import { createMimicId, DEFAULT_LAYER_ID } from "../../scada/document";

interface MimicLayerPanelProps {
  layers: MimicLayer[];
  activeLayerId: string;
  onActiveLayerChange: (layerId: string) => void;
  onUpdateLayers: (layers: MimicLayer[]) => void;
}

export default function MimicLayerPanel({
  layers,
  activeLayerId,
  onActiveLayerChange,
  onUpdateLayers,
}: MimicLayerPanelProps) {
  const { t } = useTranslation("scada");

  const patchLayer = (id: string, patch: Partial<MimicLayer>) => {
    onUpdateLayers(layers.map((layer) => (layer.id === id ? { ...layer, ...patch } : layer)));
  };

  const addLayer = () => {
    const id = createMimicId("layer");
    onUpdateLayers([
      ...layers,
      { id, name: t("layers.newLayer", { n: layers.length + 1 }), visible: true },
    ]);
    onActiveLayerChange(id);
  };

  const removeLayer = (id: string) => {
    if (layers.length <= 1 || id === DEFAULT_LAYER_ID) return;
    onUpdateLayers(layers.filter((layer) => layer.id !== id));
    if (activeLayerId === id) {
      onActiveLayerChange(DEFAULT_LAYER_ID);
    }
  };

  return (
    <div className="scada-layer-panel">
      <div className="scada-panel-header scada-panel-header-compact">
        <h2 className="scada-panel-title">{t("layers.title")}</h2>
        <button type="button" className="scada-btn-ghost scada-btn-sm" onClick={addLayer}>
          {t("layers.add")}
        </button>
      </div>
      <ul className="scada-layer-panel-list">
        {layers.map((layer) => (
          <li key={layer.id} className="scada-layer-panel-item">
            <input
              type="radio"
              name="scada-active-layer"
              checked={activeLayerId === layer.id}
              onChange={() => onActiveLayerChange(layer.id)}
              title={t("layers.activeHint")}
            />
            <input
              type="checkbox"
              checked={layer.visible}
              onChange={(e) => patchLayer(layer.id, { visible: e.target.checked })}
              title={t("layers.visibleHint")}
            />
            <input
              type="text"
              className="scada-form-input scada-layer-name-input"
              value={layer.name}
              onChange={(e) => patchLayer(layer.id, { name: e.target.value })}
            />
            <label className="scada-layer-lock" title={t("layers.lockHint")}>
              <input
                type="checkbox"
                checked={Boolean(layer.locked)}
                onChange={(e) => patchLayer(layer.id, { locked: e.target.checked })}
              />
              🔒
            </label>
            {layer.id !== DEFAULT_LAYER_ID && (
              <button
                type="button"
                className="scada-btn-ghost scada-btn-sm"
                onClick={() => removeLayer(layer.id)}
                title={t("layers.remove")}
              >
                ×
              </button>
            )}
          </li>
        ))}
      </ul>
    </div>
  );
}
