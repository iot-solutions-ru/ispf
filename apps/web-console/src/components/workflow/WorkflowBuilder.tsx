import { lazy, Suspense, useEffect, useMemo, useState } from "react";
import { createPortal } from "react-dom";
import { useTranslation } from "react-i18next";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Alert, Button, Modal, Segmented, Select, Space, Tabs, Tag, Typography } from "antd";
import {
  cancelWorkflowInstance,
  fetchWorkflow,
  fetchWorkflowSteps,
  runWorkflow,
  saveWorkflowBpmn,
  signalWorkflowInstance,
  updateWorkflowOperatorApp,
  updateWorkflowStatus,
} from "../../api";
import { fetchAuthMe } from "../../api";
import { fetchOperatorApps } from "../../api/operatorApps";
import type { WorkflowLifecycleStatus, WorkflowStepSummary } from "../../types/workflow";
import { parseInstanceState } from "../../types/workflow";
import { usePersistentTab } from "../../hooks/usePersistentTab";

const BpmnDiagramEditor = lazy(() => import("./BpmnDiagramEditor"));
const BpmnDiagramViewer = lazy(() => import("./BpmnDiagramViewer"));
const WorkflowIspfActionsReference = lazy(() => import("./WorkflowIspfActionsReference"));

interface WorkflowBuilderProps {
  path: string;
  onClose: () => void;
  onOpenProperties?: () => void;
}

const STATUS_KEYS: Record<WorkflowLifecycleStatus, string> = {
  DRAFT: "status.draft",
  ACTIVE: "status.active",
  STOPPED: "status.stopped",
};

const STATUS_TAG_COLORS: Record<WorkflowLifecycleStatus, string> = {
  DRAFT: "default",
  ACTIVE: "success",
  STOPPED: "error",
};

function instanceStatusColor(status?: string) {
  switch (status) {
    case "RUNNING":
      return "processing";
    case "WAITING":
      return "warning";
    case "COMPLETED":
      return "success";
    case "FAILED":
    case "CANCELLED":
      return "error";
    default:
      return "default";
  }
}

type BpmnTab = "diagram" | "xml";
const BPMN_TABS: readonly BpmnTab[] = ["diagram", "xml"];

export default function WorkflowBuilder({
  path,
  onClose,
  onOpenProperties,
}: WorkflowBuilderProps) {
  const { t } = useTranslation(["workflow", "common"]);
  const queryClient = useQueryClient();
  const [mode, setMode] = useState<"view" | "edit">("view");
  const [bpmnTab, setBpmnTab] = usePersistentTab<BpmnTab>(
    `workflow:${path}`,
    "diagram",
    BPMN_TABS
  );
  const [draftBpmn, setDraftBpmn] = useState<string | null>(null);
  const [cancelModalOpen, setCancelModalOpen] = useState(false);
  const [cancelReason, setCancelReason] = useState("");

  const workflow = useQuery({
    queryKey: ["workflow", path],
    queryFn: () => fetchWorkflow(path),
  });

  const operatorApps = useQuery({
    queryKey: ["operator-apps"],
    queryFn: fetchOperatorApps,
    staleTime: 60_000,
  });

  const bpmnXml = draftBpmn ?? workflow.data?.bpmnXml ?? "";
  const instance = useMemo(
    () => parseInstanceState(workflow.data?.instanceState),
    [workflow.data?.instanceState]
  );
  const stepsQuery = useQuery({
    queryKey: ["workflow-steps", instance.instanceId],
    queryFn: () => fetchWorkflowSteps(instance.instanceId!),
    enabled: Boolean(instance.instanceId),
  });
  const dirty = draftBpmn !== null;

  const saveMutation = useMutation({
    mutationFn: () => saveWorkflowBpmn(path, bpmnXml),
    onSuccess: (data) => {
      setDraftBpmn(null);
      queryClient.setQueryData(["workflow", path], data);
    },
  });

  const statusMutation = useMutation({
    mutationFn: (status: WorkflowLifecycleStatus) => updateWorkflowStatus(path, status),
    onSuccess: (data) => queryClient.setQueryData(["workflow", path], data),
  });

  const runMutation = useMutation({
    mutationFn: () => runWorkflow(path),
    onSuccess: (data) => {
      queryClient.setQueryData(["workflow", path], data);
      queryClient.invalidateQueries({ queryKey: ["object-editor", path] });
    },
  });

  const operatorAppMutation = useMutation({
    mutationFn: (operatorAppId: string) => updateWorkflowOperatorApp(path, operatorAppId),
    onSuccess: (data) => queryClient.setQueryData(["workflow", path], data),
  });

  const authQuery = useQuery({
    queryKey: ["auth-me"],
    queryFn: fetchAuthMe,
    staleTime: 60_000,
  });

  const refreshWorkflow = (data: Awaited<ReturnType<typeof fetchWorkflow>>) => {
    queryClient.setQueryData(["workflow", path], data);
    queryClient.invalidateQueries({ queryKey: ["object-editor", path] });
  };

  const cancelMutation = useMutation({
    mutationFn: (reason?: string) => {
      if (!instance.instanceId) {
        throw new Error("No instance");
      }
      return cancelWorkflowInstance(instance.instanceId, {
        reason: reason?.trim() || undefined,
        cancelledBy: authQuery.data?.principal,
      });
    },
    onSuccess: () => {
      setCancelModalOpen(false);
      setCancelReason("");
      void workflow.refetch().then((result) => {
        if (result.data) {
          refreshWorkflow(result.data);
        }
      });
    },
  });

  const signalMutation = useMutation({
    mutationFn: (signal: string) => {
      if (!instance.instanceId) {
        throw new Error("No instance");
      }
      return signalWorkflowInstance(instance.instanceId, {
        signal,
        operatorId: authQuery.data?.principal,
      });
    },
    onSuccess: () => {
      void workflow.refetch().then((result) => {
        if (result.data) {
          refreshWorkflow(result.data);
        }
      });
    },
  });

  const canCancel =
    instance.instanceId
    && (instance.status === "WAITING" || instance.status === "RUNNING");
  const canSignal =
    instance.instanceId
    && instance.status === "WAITING"
    && Boolean(instance.pendingSignalName?.trim());

  const bpmnFullscreen = mode === "edit";

  useEffect(() => {
    if (!bpmnFullscreen) return;
    document.body.classList.add("workflow-bpmn-editor-open");
    return () => {
      document.body.classList.remove("workflow-bpmn-editor-open");
    };
  }, [bpmnFullscreen]);

  useEffect(() => {
    if (!bpmnFullscreen) return;
    const onKeyDown = (e: KeyboardEvent) => {
      const tag = (e.target as HTMLElement | null)?.tagName;
      if (tag === "INPUT" || tag === "TEXTAREA" || tag === "SELECT") return;
      if ((e.target as HTMLElement | null)?.isContentEditable) return;
      if (e.key === "Escape") {
        e.preventDefault();
        setMode("view");
      }
    };
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [bpmnFullscreen]);

  if (workflow.isLoading) {
    return (
      <div className="workflow-shell loading">
        <Typography.Text type="secondary">{t("workflow:loading")}</Typography.Text>
      </div>
    );
  }

  if (workflow.error) {
    return (
      <div className="workflow-shell error">
        <Alert
          type="error"
          showIcon
          title={t("workflow:loadError", { message: (workflow.error as Error).message })}
        />
      </div>
    );
  }

  const status = workflow.data?.status ?? "DRAFT";
  const title = workflow.data?.title ?? path;

  const bpmnEditorOverlay =
    bpmnFullscreen &&
    createPortal(
      <div className="workflow-bpmn-editor-overlay">
        <div
          className="workflow-bpmn-editor-fs"
          role="dialog"
          aria-modal="true"
          aria-label={t("workflow:bpmn.title")}
          data-testid="workflow-bpmn-editor-fs"
        >
          <header className="workflow-bpmn-editor-toolbar">
            <div className="workflow-bpmn-editor-brand">
              <div className="workflow-bpmn-editor-logo" aria-hidden />
              <div className="workflow-bpmn-editor-brand-text">
                <strong>{t("workflow:bpmn.title")}</strong>
                <span className="workflow-bpmn-editor-subtitle">{title}</span>
              </div>
              <Tag color="blue" title={t("workflow:betaHint")} style={{ marginInlineEnd: 0 }}>
                {t("workflow:betaBadge")}
              </Tag>
            </div>
            <Tabs
              className="workflow-bpmn-editor-tabs"
              activeKey={bpmnTab}
              onChange={(key) => setBpmnTab(key as BpmnTab)}
              items={[
                { key: "diagram", label: t("workflow:bpmn.tab.diagram") },
                { key: "xml", label: t("workflow:bpmn.tab.xml") },
              ]}
            />
            <Space className="workflow-bpmn-editor-actions" size="small" wrap>
              {dirty && (
                <Button
                  type="primary"
                  disabled={saveMutation.isPending}
                  loading={saveMutation.isPending}
                  onClick={() => saveMutation.mutate()}
                >
                  {t("workflow:saveBpmn")}
                </Button>
              )}
              <Button
                onClick={() => setMode("view")}
                title={t("workflow:bpmn.exitFullscreenHint")}
              >
                {t("workflow:bpmn.exitFullscreen")}
              </Button>
              <Button onClick={onClose}>{t("common:action.close")}</Button>
            </Space>
          </header>
          {(saveMutation.error || runMutation.error || statusMutation.error) && (
            <Alert
              type="error"
              showIcon
              style={{ margin: "0.5rem 1rem 0" }}
              title={String(
                (saveMutation.error as Error | null)?.message
                  ?? (runMutation.error as Error | null)?.message
                  ?? (statusMutation.error as Error | null)?.message
              )}
            />
          )}
          <div className="workflow-bpmn-editor-fs-body">
            {bpmnTab === "diagram" && (
              <Typography.Paragraph type="secondary" className="bpmn-hint">
                {t("workflow:bpmn.hint")}
              </Typography.Paragraph>
            )}
            {bpmnTab === "diagram" && (
              <Suspense
                fallback={
                  <Typography.Paragraph type="secondary">
                    {t("workflow:bpmn.loadingEditor")}
                  </Typography.Paragraph>
                }
              >
                <BpmnDiagramEditor
                  key={`${path}-diagram-fs`}
                  xml={bpmnXml}
                  onChange={(next) => setDraftBpmn(next)}
                />
              </Suspense>
            )}
            {bpmnTab === "xml" && (
              <textarea
                className="workflow-bpmn-editor workflow-bpmn-editor--fullscreen"
                value={bpmnXml}
                onChange={(e) => setDraftBpmn(e.target.value)}
                spellCheck={false}
              />
            )}
          </div>
        </div>
      </div>,
      document.body
    );

  return (
    <div className="workflow-shell">
      {bpmnEditorOverlay}
      <header className="dashboard-toolbar workflow-toolbar">
        <div>
          <div className="dashboard-kicker">
            {t("workflow:kicker")}
            <Tag color="blue" title={t("workflow:betaHint")} style={{ marginInlineEnd: 0 }}>
              {t("workflow:betaBadge")}
            </Tag>
          </div>
          <Typography.Title level={2} style={{ margin: "0.15rem 0" }}>
            {title}
          </Typography.Title>
          <Typography.Text code>{path}</Typography.Text>
          <div className="workflow-status-row">
            <Tag color={STATUS_TAG_COLORS[status]}>
              {t(`workflow:${STATUS_KEYS[status]}`)}
            </Tag>
            {workflow.data?.lastRunAt && (
              <Typography.Text type="secondary">
                {t("workflow:lastRun", { time: workflow.data.lastRunAt })}
              </Typography.Text>
            )}
          </div>
        </div>
        <Space className="dashboard-toolbar-actions" size="small" wrap>
          <Segmented<"view" | "edit">
            value={mode}
            onChange={(nextMode) => {
              setMode(nextMode);
              if (nextMode === "edit") {
                setBpmnTab("diagram");
              }
            }}
            options={[
              { value: "view", label: t("common:action.view") },
              { value: "edit", label: t("common:action.editor") },
            ]}
          />
          {status !== "ACTIVE" && (
            <Button
              disabled={statusMutation.isPending}
              loading={statusMutation.isPending}
              onClick={() => statusMutation.mutate("ACTIVE")}
            >
              {t("workflow:activate")}
            </Button>
          )}
          {status === "ACTIVE" && (
            <Button
              disabled={statusMutation.isPending}
              loading={statusMutation.isPending}
              onClick={() => statusMutation.mutate("STOPPED")}
            >
              {t("workflow:stop")}
            </Button>
          )}
          <Button
            type="primary"
            disabled={runMutation.isPending}
            loading={runMutation.isPending}
            onClick={() => runMutation.mutate()}
          >
            {t("workflow:run")}
          </Button>
          {onOpenProperties && (
            <Button onClick={onOpenProperties}>
              {t("common:action.properties")}
            </Button>
          )}
          {dirty && !bpmnFullscreen && (
            <Button
              type="primary"
              disabled={saveMutation.isPending}
              loading={saveMutation.isPending}
              onClick={() => saveMutation.mutate()}
            >
              {t("workflow:saveBpmn")}
            </Button>
          )}
          <Button onClick={onClose}>
            {t("common:action.close")}
          </Button>
        </Space>
      </header>

      {(saveMutation.error || runMutation.error || statusMutation.error) && (
        <Alert
          type="error"
          showIcon
          style={{ margin: "0.75rem 1rem 0" }}
          title={String(
            (saveMutation.error as Error | null)?.message
              ?? (runMutation.error as Error | null)?.message
              ?? (statusMutation.error as Error | null)?.message
          )}
        />
      )}

      <div className="workflow-body">
        <section className="workflow-panel workflow-side-panel">
          <Typography.Title level={3} style={{ marginTop: 0 }}>
            {t("workflow:operatorApp.title")}
          </Typography.Title>
          <Typography.Paragraph type="secondary">
            {t("workflow:operatorApp.hint")}
          </Typography.Paragraph>
          <Space direction="vertical" size={4} style={{ width: "100%" }}>
            <Typography.Text type="secondary">{t("workflow:operatorApp.field")}</Typography.Text>
            <Select
              value={workflow.data?.operatorAppId ?? ""}
              disabled={operatorAppMutation.isPending}
              loading={operatorApps.isLoading || operatorAppMutation.isPending}
              onChange={(value) => operatorAppMutation.mutate(value)}
              options={[
                { value: "", label: t("common:empty.notAssigned") },
                ...(operatorApps.data ?? []).map((app) => ({
                  value: app.appId,
                  label: `${app.title} (${app.appId})`,
                })),
              ]}
              style={{ width: "100%" }}
            />
          </Space>

          <Typography.Title level={3}>{t("workflow:trigger.title")}</Typography.Title>
          <pre className="workflow-code-block">{workflow.data?.triggerJson}</pre>

          {mode === "edit" && (
            <Suspense fallback={null}>
              <WorkflowIspfActionsReference />
            </Suspense>
          )}

          <Typography.Title level={3}>{t("workflow:instance.title")}</Typography.Title>
          {!instance.instanceId ? (
            <Typography.Paragraph type="secondary">{t("workflow:instance.empty")}</Typography.Paragraph>
          ) : (
            <>
              <div className="workflow-instance-grid">
                <div>
                  <Typography.Text type="secondary">{t("common:table.id")}</Typography.Text>
                  <div>
                    <Typography.Text code>{instance.instanceId}</Typography.Text>
                  </div>
                </div>
                <div>
                  <Typography.Text type="secondary">{t("workflow:instance.status")}</Typography.Text>
                  <div>
                    {instance.status ? (
                      <Tag color={instanceStatusColor(instance.status)}>
                        {instance.status}
                      </Tag>
                    ) : (
                      t("common:empty.dash")
                    )}
                  </div>
                </div>
                <div>
                  <Typography.Text type="secondary">{t("workflow:instance.node")}</Typography.Text>
                  <div>
                    <Typography.Text code>{instance.currentNodeId ?? t("common:empty.dash")}</Typography.Text>
                  </div>
                </div>
                {instance.pendingSignalName && (
                  <div>
                    <Typography.Text type="secondary">{t("workflow:instance.pendingSignal")}</Typography.Text>
                    <div>
                      <Typography.Text code>{instance.pendingSignalName}</Typography.Text>
                    </div>
                  </div>
                )}
                {instance.assignee && (
                  <div>
                    <Typography.Text type="secondary">{t("workflow:instance.assignee")}</Typography.Text>
                    <div>{instance.assignee}</div>
                  </div>
                )}
              </div>
              {(canSignal || canCancel) && (
                <Space className="workflow-instance-actions" size="small" wrap>
                  {canSignal && instance.pendingSignalName && (
                    <Button
                      type="primary"
                      disabled={signalMutation.isPending}
                      loading={signalMutation.isPending}
                      onClick={() => signalMutation.mutate(instance.pendingSignalName!)}
                    >
                      {signalMutation.isPending
                        ? t("workflow:instance.signaling")
                        : t("workflow:instance.signal", { signal: instance.pendingSignalName })}
                    </Button>
                  )}
                  {canCancel && !cancelModalOpen && (
                    <Button
                      danger
                      disabled={cancelMutation.isPending}
                      loading={cancelMutation.isPending}
                      onClick={() => setCancelModalOpen(true)}
                    >
                      {t("workflow:instance.cancel")}
                    </Button>
                  )}
                </Space>
              )}
            </>
          )}
          <Modal
            title={t("workflow:instance.confirmCancel")}
            open={cancelModalOpen}
            onCancel={() => {
              setCancelModalOpen(false);
              setCancelReason("");
            }}
            destroyOnHidden
            footer={[
              <Button
                key="dismiss"
                disabled={cancelMutation.isPending}
                onClick={() => {
                  setCancelModalOpen(false);
                  setCancelReason("");
                }}
              >
                {t("common:action.cancel")}
              </Button>,
              <Button
                key="confirm"
                danger
                type="primary"
                disabled={cancelMutation.isPending}
                loading={cancelMutation.isPending}
                onClick={() => cancelMutation.mutate(cancelReason)}
              >
                {cancelMutation.isPending
                  ? t("workflow:instance.cancelling")
                  : t("workflow:instance.confirmCancel")}
              </Button>,
            ]}
          >
            <Space direction="vertical" size="small" style={{ width: "100%" }}>
              <Typography.Text type="secondary">{t("workflow:instance.cancelReason")}</Typography.Text>
              <input
                value={cancelReason}
                onChange={(e) => setCancelReason(e.target.value)}
                placeholder={t("workflow:instance.cancelReasonPlaceholder")}
              />
            </Space>
          </Modal>
          {(cancelMutation.error || signalMutation.error) && (
            <Alert type="error" showIcon title={String(cancelMutation.error ?? signalMutation.error)} />
          )}
          {(cancelMutation.isSuccess || signalMutation.isSuccess) && (
            <Alert type="success" showIcon title={t("workflow:instance.actionDone")} />
          )}
          {stepsQuery.data && stepsQuery.data.length > 0 && (
            <div className="workflow-step-timeline">
              <Typography.Title level={4}>{t("workflow:instance.timeline")}</Typography.Title>
              <ol className="workflow-step-list">
                {stepsQuery.data.map((step: WorkflowStepSummary) => (
                  <li key={step.id} className={`workflow-step status-${step.status.toLowerCase()}`}>
                    <div className="workflow-step-head">
                      <Typography.Text code>
                        #{step.seq} {step.nodeId}
                      </Typography.Text>
                      <Space size="small" wrap>
                        <Typography.Text type="secondary">{step.nodeType}</Typography.Text>
                        <Tag color={instanceStatusColor(step.status)}>{step.status}</Tag>
                      </Space>
                    </div>
                    {(step.inputJson || step.outputJson || step.errorJson) && (
                      <details>
                        <summary>{t("workflow:instance.stepPayload")}</summary>
                        <pre className="workflow-code-block">
                          {[
                            step.inputJson ? `in: ${step.inputJson}` : null,
                            step.outputJson ? `out: ${step.outputJson}` : null,
                            step.errorJson ? `err: ${step.errorJson}` : null,
                          ]
                            .filter(Boolean)
                            .join("\n")}
                        </pre>
                      </details>
                    )}
                  </li>
                ))}
              </ol>
            </div>
          )}
          {instance.history && instance.history.length > 0 && (
            <pre className="workflow-code-block workflow-history">{instance.history.join("\n")}</pre>
          )}
          {instance.errorMessage && (
            <Alert type="error" showIcon title={instance.errorMessage} />
          )}
        </section>

        <section className="workflow-panel workflow-bpmn-panel">
          <div className="workflow-bpmn-head">
            <Typography.Title level={3} style={{ margin: 0 }}>
              {t("workflow:bpmn.title")}
            </Typography.Title>
            {mode === "view" && (
              <Button
                type="primary"
                onClick={() => {
                  setMode("edit");
                  setBpmnTab("diagram");
                }}
              >
                {t("workflow:bpmn.openFullscreen")}
              </Button>
            )}
          </div>

          {mode === "view" && (
            <Suspense fallback={<Typography.Paragraph type="secondary">{t("workflow:bpmn.loadingDiagram")}</Typography.Paragraph>}>
              <BpmnDiagramViewer xml={bpmnXml} />
            </Suspense>
          )}

          {mode === "edit" && (
            <Typography.Paragraph type="secondary" className="bpmn-hint">
              {t("workflow:bpmn.fullscreenActive")}
            </Typography.Paragraph>
          )}
        </section>
      </div>
    </div>
  );
}
