import { useState } from "react";
import { useTranslation } from "react-i18next";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Alert, Button, Form, Input, Space, Table, Tabs } from "antd";
import {
  createBundleObjects,
  deleteBundleObjects,
  fetchDeployHistory,
  fetchApplicationEventCatalog,
  listFunctionVersions,
  rollbackDeploy,
  rollbackFunction,
  updateBundleObjects,
} from "../../api/applications";
import ApplicationBundlePanel from "./ApplicationBundlePanel";
import ApplicationLifecyclePanel from "./ApplicationLifecyclePanel";
import { ObjectPathField } from "../../ui/index";
import { formatUserDateTime } from "../../utils/ui/formatDateTime";

interface ApplicationDeployPanelProps {
  appId: string;
  displayName?: string;
  canManage: boolean;
}

type DeployTab = "bundle" | "operations";

function formatDeployedAt(value: string | undefined): string {
  if (!value) {
    return "";
  }
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : formatUserDateTime(date);
}

export default function ApplicationDeployPanel({
  appId,
  displayName,
  canManage,
}: ApplicationDeployPanelProps) {
  const { t } = useTranslation("platform");
  const queryClient = useQueryClient();
  const [tab, setTab] = useState<DeployTab>("bundle");
  const [rollbackVersion, setRollbackVersion] = useState<string | null>(null);
  const [fnObjectPath, setFnObjectPath] = useState("root.platform.devices.demo-sensor-01");
  const [fnName, setFnName] = useState("");
  const [fnRollbackVersion, setFnRollbackVersion] = useState<string | null>(null);
  const [lifecycleAction, setLifecycleAction] = useState<"create" | "update" | "delete" | null>(
    null
  );

  const historyQuery = useQuery({
    queryKey: ["deploy-history", appId],
    queryFn: () => fetchDeployHistory(appId),
    enabled: Boolean(appId),
  });

  const functionVersionsQuery = useQuery({
    queryKey: ["function-versions", appId, fnObjectPath, fnName],
    queryFn: () => listFunctionVersions(appId, fnObjectPath.trim(), fnName.trim()),
    enabled: Boolean(appId && fnObjectPath.trim() && fnName.trim()),
  });

  const eventCatalogQuery = useQuery({
    queryKey: ["application-event-catalog", appId],
    queryFn: () => fetchApplicationEventCatalog(appId),
    enabled: Boolean(appId),
  });

  const rollbackMutation = useMutation({
    mutationFn: (version: string) => rollbackDeploy(appId, version),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["deploy-history", appId] });
      queryClient.invalidateQueries({ queryKey: ["objects"] });
    },
  });

  const functionRollbackMutation = useMutation({
    mutationFn: (version: string) =>
      rollbackFunction(appId, fnObjectPath.trim(), fnName.trim(), version),
    onSuccess: () => {
      queryClient.invalidateQueries({
        queryKey: ["function-versions", appId, fnObjectPath, fnName],
      });
      queryClient.invalidateQueries({ queryKey: ["object-editor", fnObjectPath] });
    },
  });

  const invalidateAfterLifecycle = () => {
    queryClient.invalidateQueries({ queryKey: ["deploy-history", appId] });
    queryClient.invalidateQueries({ queryKey: ["objects"] });
  };

  const createObjectsMutation = useMutation({
    mutationFn: () => createBundleObjects(appId),
    onSuccess: invalidateAfterLifecycle,
  });

  const updateObjectsMutation = useMutation({
    mutationFn: () => updateBundleObjects(appId),
    onSuccess: invalidateAfterLifecycle,
  });

  const deleteObjectsMutation = useMutation({
    mutationFn: () => deleteBundleObjects(appId),
    onSuccess: invalidateAfterLifecycle,
  });

  const lifecycleMutation =
    lifecycleAction === "create"
      ? createObjectsMutation
      : lifecycleAction === "update"
        ? updateObjectsMutation
        : lifecycleAction === "delete"
          ? deleteObjectsMutation
          : null;

  const runLifecycle = (action: "create" | "update" | "delete") => {
    const confirmKey =
      action === "delete"
        ? "deploy.bundleObjectsDeleteConfirm"
        : action === "update"
          ? "deploy.bundleObjectsUpdateConfirm"
          : "deploy.bundleObjectsCreateConfirm";
    if (!window.confirm(t(confirmKey))) {
      return;
    }
    setLifecycleAction(action);
    if (action === "create") {
      createObjectsMutation.mutate();
    } else if (action === "update") {
      updateObjectsMutation.mutate();
    } else {
      deleteObjectsMutation.mutate();
    }
  };

  return (
    <div className="application-deploy-panel">
      <Tabs
        className="application-deploy-tabs"
        activeKey={tab}
        onChange={(key) => setTab(key as DeployTab)}
        aria-label={t("deploy.tabsAria")}
        items={[
          { key: "bundle", label: t("deploy.tabBundle") },
          { key: "operations", label: t("deploy.tabOperations") },
        ]}
      />

      {tab === "bundle" && (
        <ApplicationBundlePanel
          appId={appId}
          displayName={displayName}
          canManage={canManage}
          embedded
        />
      )}

      {tab === "operations" && (
        <div className="application-operations-panel">
          <section className="application-operations-section">
            <h3>{t("deploy.historyTitle")}</h3>
            <p className="op-muted">{t("deploy.historyHint")}</p>

            {historyQuery.isLoading && <p className="op-muted">{t("deploy.loadingHistory")}</p>}
            {historyQuery.error && <Alert type="error" showIcon message={String(historyQuery.error)} />}

            {historyQuery.data && historyQuery.data.length === 0 && (
              <p className="op-muted">{t("deploy.emptyHistory")}</p>
            )}

            {historyQuery.data && historyQuery.data.length > 0 && (
              <ul className="deploy-history-list">
                {historyQuery.data.map((entry) => {
                  const rollingBack =
                    rollbackMutation.isPending && rollbackVersion === entry.version;
                  return (
                    <li key={entry.version} className={entry.active ? "active-version" : ""}>
                      <span>
                        v{entry.version}
                        {entry.active ? ` (${t("deploy.active")})` : ""}
                      </span>
                      <span className="op-muted">{formatDeployedAt(entry.deployedAt)}</span>
                      {canManage && !entry.active && (
                        <Button
                          disabled={rollbackMutation.isPending}
                          onClick={() => {
                            setRollbackVersion(entry.version);
                            rollbackMutation.mutate(entry.version);
                          }}
                        >
                          {rollingBack ? t("deploy.rollingBack") : t("deploy.rollbackBundle")}
                        </Button>
                      )}
                    </li>
                  );
                })}
              </ul>
            )}

            {rollbackMutation.error && <Alert type="error" showIcon message={String(rollbackMutation.error)} />}
            {rollbackMutation.isSuccess && (
              <Alert
                type="success"
                showIcon
                message={
                  <>
                {t("deploy.rollbackSuccess")}
                {rollbackMutation.data?.rolledBackTo
                  ? ` → ${t("deploy.versionLabel", { version: rollbackMutation.data.rolledBackTo })}`
                  : ""}
                  </>
                }
              />
            )}
          </section>

          <section className="application-operations-section">
            <h3>{t("deploy.eventCatalogTitle")}</h3>
            <p className="op-muted">{t("deploy.eventCatalogHint")}</p>
            {eventCatalogQuery.isLoading && (
              <p className="op-muted">{t("deploy.eventCatalogLoading")}</p>
            )}
            {eventCatalogQuery.error && <Alert type="error" showIcon message={String(eventCatalogQuery.error)} />}
            {eventCatalogQuery.data && eventCatalogQuery.data.length === 0 && (
              <p className="op-muted">{t("deploy.eventCatalogEmpty")}</p>
            )}
            {eventCatalogQuery.data && eventCatalogQuery.data.length > 0 && (
              <Table
                size="small"
                pagination={false}
                rowKey="id"
                dataSource={eventCatalogQuery.data}
                columns={[
                  {
                    title: t("deploy.eventCatalogId"),
                    dataIndex: "id",
                    render: (value: string) => <code>{value}</code>,
                  },
                  {
                    title: t("deploy.eventCatalogRoles"),
                    render: (_, entry) =>
                      (entry.roles ?? []).length > 0
                        ? entry.roles!.join(", ")
                        : t("deploy.eventCatalogAnyRole"),
                  },
                ]}
              />
            )}
          </section>

          <section className="application-operations-section">
            <h3>{t("deploy.bundleObjectsTitle")}</h3>
            <p className="op-muted">{t("deploy.bundleObjectsHint")}</p>
            {canManage && (
              <Space wrap className="bundle-object-actions">
                <Button
                  disabled={Boolean(lifecycleMutation?.isPending)}
                  onClick={() => runLifecycle("create")}
                >
                  {lifecycleAction === "create" && createObjectsMutation.isPending
                    ? t("deploy.bundleObjectsCreating")
                    : t("deploy.bundleObjectsCreate")}
                </Button>
                <Button
                  disabled={Boolean(lifecycleMutation?.isPending)}
                  onClick={() => runLifecycle("update")}
                >
                  {lifecycleAction === "update" && updateObjectsMutation.isPending
                    ? t("deploy.bundleObjectsUpdating")
                    : t("deploy.bundleObjectsUpdate")}
                </Button>
                <Button
                  danger
                  disabled={Boolean(lifecycleMutation?.isPending)}
                  onClick={() => runLifecycle("delete")}
                >
                  {lifecycleAction === "delete" && deleteObjectsMutation.isPending
                    ? t("deploy.bundleObjectsDeleting")
                    : t("deploy.bundleObjectsDelete")}
                </Button>
              </Space>
            )}

            {lifecycleMutation?.error && <Alert type="error" showIcon message={String(lifecycleMutation.error)} />}
            {lifecycleMutation?.isSuccess && lifecycleMutation.data && (
              <Alert
                type="success"
                showIcon
                message={
                  <>
                {t("deploy.bundleObjectsSuccess", {
                  action: lifecycleMutation.data.action ?? lifecycleAction ?? "",
                  status: lifecycleMutation.data.status ?? "OK",
                })}
                {(lifecycleMutation.data.applied?.length ?? 0) > 0 && (
                  <span>
                    {" "}
                    {t("deploy.bundleObjectsAppliedCount", {
                      count: lifecycleMutation.data.applied?.length ?? 0,
                    })}
                  </span>
                )}
                {(lifecycleMutation.data.removed?.length ?? 0) > 0 && (
                  <span>
                    {" "}
                    {t("deploy.bundleObjectsRemovedCount", {
                      count: lifecycleMutation.data.removed?.length ?? 0,
                    })}
                  </span>
                )}
                {(lifecycleMutation.data.skipped?.length ?? 0) > 0 && (
                  <span>
                    {" "}
                    {t("deploy.bundleObjectsSkippedCount", {
                      count: lifecycleMutation.data.skipped?.length ?? 0,
                    })}
                  </span>
                )}
                  </>
                }
              />
            )}
          </section>

          <section className="application-operations-section">
            <h3>{t("deploy.functionVersionsTitle")}</h3>
            <p className="op-muted">{t("deploy.functionVersionsHint")}</p>
            <Form component="div" layout="vertical" className="application-deploy-function-form">
              <ObjectPathField
                label="objectPath"
                value={fnObjectPath}
                onChange={setFnObjectPath}
              />
              <label>
                functionName
                <Input
                  value={fnName}
                  onChange={(e) => setFnName(e.target.value)}
                  placeholder="myFunction"
                />
              </label>
            </Form>

            {functionVersionsQuery.isFetching && fnName.trim() && (
              <p className="op-muted">{t("deploy.loadingVersions")}</p>
            )}
            {functionVersionsQuery.error && (
              <Alert type="error" showIcon message={String(functionVersionsQuery.error)} />
            )}

            {functionVersionsQuery.data && functionVersionsQuery.data.length > 0 && (
              <ul className="deploy-history-list">
                {functionVersionsQuery.data.map((entry) => {
                  const rollingBack =
                    functionRollbackMutation.isPending && fnRollbackVersion === entry.version;
                  return (
                    <li key={entry.version} className={entry.active ? "active-version" : ""}>
                      <span>
                        v{entry.version}
                        {entry.active ? ` (${t("deploy.active")})` : ""}
                      </span>
                      {entry.deployedAt && (
                        <span className="op-muted">{formatDeployedAt(entry.deployedAt)}</span>
                      )}
                      {canManage && !entry.active && (
                        <Button
                          disabled={functionRollbackMutation.isPending}
                          onClick={() => {
                            setFnRollbackVersion(entry.version);
                            functionRollbackMutation.mutate(entry.version);
                          }}
                        >
                          {rollingBack ? t("deploy.deploying") : t("deploy.deployPrevious")}
                        </Button>
                      )}
                    </li>
                  );
                })}
              </ul>
            )}

            {functionRollbackMutation.error && (
              <Alert type="error" showIcon message={String(functionRollbackMutation.error)} />
            )}
            {functionRollbackMutation.isSuccess && (
              <Alert
                type="success"
                showIcon
                message={t("deploy.functionActiveVersion", { version: functionRollbackMutation.data?.version })}
              />
            )}
          </section>

          <ApplicationLifecyclePanel appId={appId} canManage={canManage} />
        </div>
      )}
    </div>
  );
}
