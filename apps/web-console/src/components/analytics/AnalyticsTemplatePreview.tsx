import { useMemo } from "react";
import { useQuery } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { fetchVariableHistoryAggregate } from "../../api";
import { buildAnalyticsBindingExpression } from "../../utils/analyticsChartBinding";
import type { AnalyticsTemplateDto } from "../../api";

type AnalyticsTemplatePreviewProps = {
  template: AnalyticsTemplateDto;
};

export function AnalyticsTemplatePreview({ template }: AnalyticsTemplatePreviewProps) {
  const { t } = useTranslation("automation");
  const sourcePath = template.sourcePath?.trim() || "";
  const sourceVariable = template.sourceVariable?.trim() || "";
  const canPreview = Boolean(sourcePath && sourceVariable);

  const aggregateQuery = useQuery({
    queryKey: [
      "analytics-template-preview",
      sourcePath,
      sourceVariable,
      template.sourceField,
      template.windowBucket,
    ],
    queryFn: () =>
      fetchVariableHistoryAggregate(sourcePath, sourceVariable, {
        field: template.sourceField || "value",
        from: new Date(Date.now() - 24 * 60 * 60 * 1000).toISOString(),
        to: new Date().toISOString(),
        bucket: template.windowBucket || "5m",
      }),
    enabled: canPreview,
    staleTime: 30_000,
  });

  const expression = useMemo(() => {
    if (!sourceVariable) {
      return "";
    }
    return buildAnalyticsBindingExpression(
      template.helper,
      sourceVariable,
      template.windowBucket || "5m",
    );
  }, [sourceVariable, template.helper, template.windowBucket]);

  const points = useMemo(() => {
    return (aggregateQuery.data?.buckets ?? [])
      .filter((bucket) => bucket.avg != null && Number.isFinite(bucket.avg))
      .map((bucket) => ({
        time: bucket.ts,
        value: bucket.avg as number,
      }));
  }, [aggregateQuery.data]);

  if (!canPreview) {
    return (
      <p className="hint">
        {t("analyticsTemplate.previewNeedsSource")}
      </p>
    );
  }

  return (
    <section className="analytics-preview">
      <p className="hint">
        {t("analyticsTemplate.previewExpression")}: <code>{expression}</code>
      </p>
      {aggregateQuery.isLoading ? <p className="hint">{t("analyticsTemplate.previewLoading")}</p> : null}
      {aggregateQuery.error ? (
        <p className="hint error">{String(aggregateQuery.error)}</p>
      ) : null}
      {points.length === 0 && !aggregateQuery.isLoading ? (
        <p className="hint">{t("analyticsTemplate.previewEmpty")}</p>
      ) : (
        <div className="analytics-preview__chart" aria-hidden>
          <svg viewBox="0 0 320 80" className="analytics-preview__svg">
            {renderSparkline(points)}
          </svg>
          <p className="hint">
            {t("analyticsTemplate.previewLastValue", {
              value: points[points.length - 1]?.value ?? "—",
              count: points.length,
            })}
          </p>
        </div>
      )}
    </section>
  );
}

function renderSparkline(points: Array<{ time: string; value: number }>) {
  if (points.length === 0) {
    return null;
  }
  const values = points.map((point) => point.value);
  const min = Math.min(...values);
  const max = Math.max(...values);
  const span = max - min || 1;
  const coords = points.map((point, index) => {
    const x = points.length === 1 ? 160 : (index / (points.length - 1)) * 300 + 10;
    const y = 70 - ((point.value - min) / span) * 60;
    return `${x},${y}`;
  });
  return <polyline fill="none" stroke="currentColor" strokeWidth="2" points={coords.join(" ")} />;
}
