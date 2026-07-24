import { Button, Input, Tooltip } from "antd";
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
        <Button type="text" size="small" className="scada-btn-ghost scada-btn-sm" onClick={addLayer}>
          {t("layers.add")}
        </Button>
      </div>
      <ul className="scada-layer-panel-list">
        {layers.map((layer) => (
          <li key={layer.id} className="scada-layer-panel-item">
            <span className="scada-layer-controls">
              <Tooltip title={t("layers.activeHint")}>
                <input
                  type="radio"
                  name="scada-active-layer"
                  checked={activeLayerId === layer.id}
                  onChange={() => onActiveLayerChange(layer.id)}
                />
              </Tooltip>
              <Tooltip title={t("layers.visibleHint")}>
                <input
                  type="checkbox"
                  checked={layer.visible}
                  onChange={(e) => patchLayer(layer.id, { visible: e.target.checked })}
                />
              </Tooltip>
            </span>
            <Input
              className="scada-form-input scada-layer-name-input"
              value={layer.name}
              onChange={(e) => patchLayer(layer.id, { name: e.target.value })}
            />
            <Tooltip title={t("layers.lockHint")}>
              <label className="scada-layer-lock">
                <input
                  type="checkbox"
                  checked={Boolean(layer.locked)}
                  onChange={(e) => patchLayer(layer.id, { locked: e.target.checked })}
                />
                🔒
              </label>
            </Tooltip>
            {layer.id !== DEFAULT_LAYER_ID && (
              <Button
                type="text"
                size="small"
                className="scada-btn-ghost scada-btn-sm"
                onClick={() => removeLayer(layer.id)}
                title={t("layers.remove")}
              >
                ×
              </Button>
            )}
          </li>
        ))}
      </ul>
    </div>
  );
}
