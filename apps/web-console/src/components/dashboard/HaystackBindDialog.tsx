import { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useMutation } from "@tanstack/react-query";
import { Alert, Button, Checkbox, Input, Modal, Radio, Space, Typography } from "antd";
import { COMMON_HAYSTACK_MARKERS } from "../../constants/haystackMarkers";
import {
  queryHaystackFilter,
  searchHaystackTags,
  type HaystackTagSearchMatch,
} from "../../api/haystackSearch";
import type { DashboardLayout, DashboardWidget } from "../../types/dashboard";
import { newWidget } from "../../types/dashboard";
import { nextWidgetZIndex } from "./widgetLayerUtils";

interface HaystackBindDialogProps {
  layout: DashboardLayout;
  onApply: (layout: DashboardLayout) => void;
  onClose: () => void;
}

type BindMode = "tags" | "filter";

const DEFAULT_ROOT_PATH = "root.platform";
const BIND_TAGS = ["equip", "point", "temp"] as const;
const DEFAULT_FILTER = "equip and point and temp";

function appendValueWidgets(
  layout: DashboardLayout,
  matches: HaystackTagSearchMatch[]
): DashboardLayout {
  const pointMatches = matches.filter(
    (match) => match.entityKind === "point" && match.variableName
  );
  if (pointMatches.length === 0) {
    return layout;
  }
  const columns = layout.columns ?? 12;
  const widgetW = 4;
  const widgetH = 2;
  const baseY = layout.widgets.reduce((max, widget) => Math.max(max, widget.y + widget.h), 0);
  const baseZ = nextWidgetZIndex(layout.widgets);
  const newWidgets: DashboardWidget[] = pointMatches.map((match, index) => {
    const slot = layout.widgets.length + index;
    const x = (slot * widgetW) % columns;
    const row = Math.floor((slot * widgetW) / columns);
    return {
      ...newWidget("value", slot),
      id: `haystack-${match.variableName}-${index}-${Date.now()}`,
      title: match.dis || match.variableName || match.objectPath,
      objectPath: match.objectPath,
      variableName: match.variableName,
      valueField: "value",
      decimals: 2,
      x,
      y: baseY + row * widgetH,
      w: widgetW,
      h: widgetH,
      zIndex: baseZ + index,
      visible: true,
    };
  });
  return { ...layout, widgets: [...layout.widgets, ...newWidgets] };
}

export default function HaystackBindDialog({ layout, onApply, onClose }: HaystackBindDialogProps) {
  const { t } = useTranslation(["dashboard", "inspector", "common"]);
  const [mode, setMode] = useState<BindMode>("filter");
  const [selectedTags, setSelectedTags] = useState<string[]>([...BIND_TAGS]);
  const [filterQuery, setFilterQuery] = useState(DEFAULT_FILTER);
  const [rootPath, setRootPath] = useState(DEFAULT_ROOT_PATH);
  const [matches, setMatches] = useState<HaystackTagSearchMatch[]>([]);

  const searchMutation = useMutation({
    mutationFn: async () => {
      if (mode === "filter") {
        const trimmed = filterQuery.trim();
        if (!trimmed) {
          throw new Error("empty filter");
        }
        const result = await queryHaystackFilter({
          filter: trimmed,
          rootPath,
          entityKind: "point",
          limit: 50,
        });
        return result.matches;
      }
      const result = await searchHaystackTags({
        tags: selectedTags,
        rootPath,
        entityKind: "point",
        limit: 50,
      });
      return result.matches;
    },
    onSuccess: (result) => {
      setMatches(result);
    },
  });

  const pointCount = useMemo(
    () => matches.filter((match) => match.entityKind === "point" && match.variableName).length,
    [matches]
  );

  const toggleTag = (tag: string) => {
    setSelectedTags((current) =>
      current.includes(tag) ? current.filter((item) => item !== tag) : [...current, tag]
    );
  };

  const canSearch =
    mode === "filter" ? filterQuery.trim().length > 0 : selectedTags.length > 0;

  const handleSearch = () => {
    if (!canSearch) {
      return;
    }
    searchMutation.mutate();
  };

  const handleBind = () => {
    onApply(appendValueWidgets(layout, matches));
    onClose();
  };

  return (
    <Modal
      title={t("haystackBind.title")}
      open
      onCancel={onClose}
      className="haystack-bind-dialog"
      width={720}
      destroyOnHidden
      footer={
        <Space>
          <Button onClick={onClose}>
            {t("common:action.cancel")}
          </Button>
          <Button
            type="primary"
            disabled={pointCount === 0}
            onClick={handleBind}
          >
            {t("haystackBind.addWidgets", { count: pointCount })}
          </Button>
        </Space>
      }
    >
      <Typography.Paragraph type="secondary">{t("haystackBind.intro")}</Typography.Paragraph>

      <Radio.Group
        className="haystack-bind-mode"
        aria-label={t("haystackBind.modeLabel")}
        value={mode}
        onChange={(event) => setMode(event.target.value as BindMode)}
      >
        <Radio value="filter">{t("haystackBind.modeFilter")}</Radio>
        <Radio value="tags">{t("haystackBind.modeTags")}</Radio>
      </Radio.Group>

      {mode === "filter" ? (
        <label className="property-field">
          {t("haystackBind.filterQuery")}
          <Input
            value={filterQuery}
            onChange={(event) => setFilterQuery(event.target.value)}
            placeholder={DEFAULT_FILTER}
            spellCheck={false}
          />
          <span className="hint">{t("haystackBind.filterHint")}</span>
        </label>
      ) : (
        <fieldset className="function-form-multiselect haystack-marker-fieldset">
          <legend>{t("inspector:haystack.tags")}</legend>
          {COMMON_HAYSTACK_MARKERS.map((tag) => (
            <Checkbox
              key={tag}
              className="checkbox-inline"
              checked={selectedTags.includes(tag)}
              onChange={() => toggleTag(tag)}
            >
              {t(`inspector:haystack.marker.${tag}`, tag)}
            </Checkbox>
          ))}
        </fieldset>
      )}

      <label className="property-field">
        {t("haystackBind.rootPath")}
        <Input
          value={rootPath}
          onChange={(event) => setRootPath(event.target.value)}
          placeholder={DEFAULT_ROOT_PATH}
        />
      </label>

      <div className="haystack-bind-actions">
        <Button
          type="primary"
          disabled={!canSearch || searchMutation.isPending}
          loading={searchMutation.isPending}
          onClick={handleSearch}
        >
          {searchMutation.isPending ? t("haystackBind.searching") : t("haystackBind.search")}
        </Button>
      </div>

      {searchMutation.isError && (
        <Alert type="error" message={t("haystackBind.error")} />
      )}

      {matches.length > 0 && (
        <Typography.Paragraph type="secondary">
          {t("haystackBind.results", { count: pointCount })}
        </Typography.Paragraph>
      )}

      {pointCount > 0 && (
        <ul className="haystack-bind-preview">
          {matches
            .filter((match) => match.entityKind === "point" && match.variableName)
            .slice(0, 8)
            .map((match) => (
              <li key={`${match.objectPath}.${match.variableName}`}>
                {match.dis || match.variableName} — {match.objectPath}
              </li>
            ))}
        </ul>
      )}
    </Modal>
  );
}
