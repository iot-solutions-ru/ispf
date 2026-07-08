import { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  applyChangeSet,
  createChangeSet,
  fetchChangeSet,
  fetchChangeSets,
  previewChangeSet,
  type ChangeSetOp,
  type ChangeSetPreview,
} from "../../api/platformChangeSets";
import { useUserTimeZone } from "../../context/UserTimeZoneContext";

const SAMPLE_OPS = `[
  {
    "op": "UPDATE_INFO",
    "path": "root.platform",
    "expectedRevision": 1,
    "payload": { "displayName": "Platform", "description": "Updated via change-set" }
  }
]`;

export default function PlatformChangeSetsPanel() {
  const { t } = useTranslation(["system", "common"]);
  const { formatDate } = useUserTimeZone();
  const queryClient = useQueryClient();
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [statusFilter, setStatusFilter] = useState("");
  const [createTitle, setCreateTitle] = useState("");
  const [createOpsJson, setCreateOpsJson] = useState(SAMPLE_OPS);
  const [createError, setCreateError] = useState<string | null>(null);
  const [preview, setPreview] = useState<ChangeSetPreview | null>(null);
  const [applyForce, setApplyForce] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);

  const listQuery = useQuery({
    queryKey: ["platform-change-sets", statusFilter],
    queryFn: () => fetchChangeSets(statusFilter || undefined),
  });

  const detailQuery = useQuery({
    queryKey: ["platform-change-set", selectedId],
    queryFn: () => fetchChangeSet(selectedId as string),
    enabled: Boolean(selectedId),
  });

  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: ["platform-change-sets"] });
    if (selectedId) {
      queryClient.invalidateQueries({ queryKey: ["platform-change-set", selectedId] });
    }
  };

  const createMutation = useMutation({
    mutationFn: async () => {
      let ops: ChangeSetOp[];
      try {
        ops = JSON.parse(createOpsJson) as ChangeSetOp[];
      } catch {
        throw new Error(t("changeSets.invalidOpsJson"));
      }
      if (!Array.isArray(ops) || ops.length === 0) {
        throw new Error(t("changeSets.opsRequired"));
      }
      return createChangeSet(createTitle.trim(), ops);
    },
    onSuccess: (created) => {
      setCreateTitle("");
      setCreateError(null);
      setSelectedId(created.id);
      invalidate();
    },
    onError: (error) => setCreateError((error as Error).message),
  });

  const previewMutation = useMutation({
    mutationFn: () => previewChangeSet(selectedId as string),
    onSuccess: (result) => {
      setPreview(result);
      setActionError(null);
    },
    onError: (error) => setActionError((error as Error).message),
  });

  const applyMutation = useMutation({
    mutationFn: () => applyChangeSet(selectedId as string, applyForce),
    onSuccess: () => {
      setPreview(null);
      setActionError(null);
      invalidate();
    },
    onError: (error) => setActionError((error as Error).message),
  });

  const selectedSummary = useMemo(
    () => listQuery.data?.find((item) => item.id === selectedId) ?? null,
    [listQuery.data, selectedId]
  );

  return (
    <section className="system-panel platform-change-sets-panel">
      <p className="op-muted">{t("changeSets.intro")}</p>

      <div className="platform-change-sets-toolbar">
        <label>
          {t("changeSets.statusFilter")}
          <select value={statusFilter} onChange={(e) => setStatusFilter(e.target.value)}>
            <option value="">{t("changeSets.statusAll")}</option>
            <option value="DRAFT">DRAFT</option>
            <option value="APPLIED">APPLIED</option>
          </select>
        </label>
        <button type="button" className="btn" onClick={() => listQuery.refetch()}>
          {t("common:action.refresh")}
        </button>
      </div>

      {listQuery.isLoading ? (
        <p className="hint">{t("changeSets.loading")}</p>
      ) : listQuery.isError ? (
        <p className="hint">{t("changeSets.loadError")}</p>
      ) : (
        <div className="platform-change-sets-layout">
          <div className="platform-change-sets-list">
            <table className="data-table">
              <thead>
                <tr>
                  <th>{t("changeSets.column.title")}</th>
                  <th>{t("changeSets.column.status")}</th>
                  <th>{t("changeSets.column.author")}</th>
                  <th>{t("changeSets.column.updated")}</th>
                </tr>
              </thead>
              <tbody>
                {(listQuery.data ?? []).map((item) => (
                  <tr
                    key={item.id}
                    className={selectedId === item.id ? "selected" : ""}
                    onClick={() => {
                      setSelectedId(item.id);
                      setPreview(null);
                      setActionError(null);
                    }}
                  >
                    <td>{item.title}</td>
                    <td>
                      <code>{item.status}</code>
                    </td>
                    <td>{item.author}</td>
                    <td>{formatDate(item.updatedAt)}</td>
                  </tr>
                ))}
                {(listQuery.data ?? []).length === 0 && (
                  <tr>
                    <td colSpan={4} className="hint">
                      {t("changeSets.empty")}
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>

          <div className="platform-change-sets-detail">
            <h3>{t("changeSets.createTitle")}</h3>
            <label>
              {t("changeSets.field.title")}
              <input
                value={createTitle}
                onChange={(e) => setCreateTitle(e.target.value)}
                placeholder={t("changeSets.titlePlaceholder")}
              />
            </label>
            <label>
              {t("changeSets.field.opsJson")}
              <textarea
                className="mono"
                rows={8}
                value={createOpsJson}
                onChange={(e) => setCreateOpsJson(e.target.value)}
              />
            </label>
            {createError && <div className="banner error">{createError}</div>}
            <button
              type="button"
              className="btn primary"
              disabled={!createTitle.trim() || createMutation.isPending}
              onClick={() => createMutation.mutate()}
            >
              {createMutation.isPending ? t("changeSets.creating") : t("changeSets.create")}
            </button>

            {selectedId && (
              <>
                <h3>{selectedSummary?.title ?? selectedId}</h3>
                {detailQuery.isLoading ? (
                  <p className="hint">{t("common:action.loading")}</p>
                ) : detailQuery.data ? (
                  <>
                    <p className="hint">
                      <code>{detailQuery.data.status}</code> · {detailQuery.data.author}
                    </p>
                    <table className="data-table compact">
                      <thead>
                        <tr>
                          <th>{t("changeSets.column.op")}</th>
                          <th>{t("changeSets.column.path")}</th>
                          <th>{t("changeSets.column.revision")}</th>
                        </tr>
                      </thead>
                      <tbody>
                        {detailQuery.data.ops.map((op, index) => (
                          <tr key={`${op.path}-${index}`}>
                            <td>
                              <code>{op.op}</code>
                            </td>
                            <td className="mono">{op.path}</td>
                            <td>{op.expectedRevision ?? "—"}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                    <div className="platform-change-sets-actions">
                      <button
                        type="button"
                        className="btn"
                        disabled={previewMutation.isPending}
                        onClick={() => previewMutation.mutate()}
                      >
                        {previewMutation.isPending ? t("changeSets.previewing") : t("changeSets.preview")}
                      </button>
                      <label className="inline-check">
                        <input
                          type="checkbox"
                          checked={applyForce}
                          onChange={(e) => setApplyForce(e.target.checked)}
                        />
                        {t("changeSets.forceApply")}
                      </label>
                      <button
                        type="button"
                        className="btn primary"
                        disabled={
                          detailQuery.data.status === "APPLIED" || applyMutation.isPending
                        }
                        onClick={() => applyMutation.mutate()}
                      >
                        {applyMutation.isPending ? t("changeSets.applying") : t("changeSets.apply")}
                      </button>
                    </div>
                  </>
                ) : null}

                {preview && (
                  <div className="platform-change-sets-preview">
                    <h4>{t("changeSets.previewResult")}</h4>
                    <p>
                      {t("changeSets.conflictCount", { count: preview.conflictCount })}
                    </p>
                    {preview.conflicts.length > 0 && (
                      <table className="data-table compact">
                        <thead>
                          <tr>
                            <th>{t("changeSets.column.path")}</th>
                            <th>{t("changeSets.column.op")}</th>
                            <th>{t("changeSets.column.expected")}</th>
                            <th>{t("changeSets.column.current")}</th>
                          </tr>
                        </thead>
                        <tbody>
                          {preview.conflicts.map((row, index) => (
                            <tr key={index}>
                              <td className="mono">{String(row.path ?? "")}</td>
                              <td>
                                <code>{String(row.op ?? "")}</code>
                              </td>
                              <td>{String(row.expectedRevision ?? "")}</td>
                              <td>{String(row.currentRevision ?? "")}</td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    )}
                    {preview.applicable.length > 0 && (
                      <p className="hint">
                        {t("changeSets.applicableCount", { count: preview.applicable.length })}
                      </p>
                    )}
                  </div>
                )}
                {actionError && <div className="banner error">{actionError}</div>}
                {applyMutation.isSuccess && (
                  <div className="banner success">{t("changeSets.appliedSuccess")}</div>
                )}
              </>
            )}
          </div>
        </div>
      )}
    </section>
  );
}
