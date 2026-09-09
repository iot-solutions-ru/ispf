import { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Alert, Button, Checkbox, Input, Space, Upload } from "antd";
import type { UploadProps } from "antd";
import {
  browseDriverCatalog,
  deleteDriverCatalogArtifact,
  importDriverCatalogArtifact,
  importDriverCatalogPoints,
  listDriverCatalogArtifacts,
  type DriverCatalogNode,
  type DriverPointSelection,
} from "../api/drivers";

interface SnmpMibPanelProps {
  devicePath: string;
  canManage: boolean;
  onImported?: () => void;
}

type SelectedRow = {
  node: DriverCatalogNode;
  index: string;
};

export default function SnmpMibPanel({ devicePath, canManage, onImported }: SnmpMibPanelProps) {
  const { t } = useTranslation(["inspector", "common"]);
  const queryClient = useQueryClient();
  const [moduleId, setModuleId] = useState<string | null>(null);
  const [selected, setSelected] = useState<Record<string, SelectedRow>>({});
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const artifactsQuery = useQuery({
    queryKey: ["driver-catalog-artifacts", "snmp"],
    queryFn: () => listDriverCatalogArtifacts("snmp"),
    enabled: canManage,
  });

  const modulesQuery = useQuery({
    queryKey: ["driver-catalog-browse", "snmp", "root"],
    queryFn: () => browseDriverCatalog(undefined, "snmp"),
    enabled: canManage,
  });

  const objectsQuery = useQuery({
    queryKey: ["driver-catalog-browse", "snmp", moduleId],
    queryFn: () => browseDriverCatalog(moduleId ?? undefined, "snmp"),
    enabled: canManage && !!moduleId,
  });

  const uploadMutation = useMutation({
    mutationFn: async ({ fileName, text }: { fileName: string; text: string }) =>
      importDriverCatalogArtifact(fileName, text, "snmp"),
    onSuccess: async () => {
      setError(null);
      setMessage(t("inspector:driver.mib.uploadOk"));
      await queryClient.invalidateQueries({ queryKey: ["driver-catalog-artifacts", "snmp"] });
      await queryClient.invalidateQueries({ queryKey: ["driver-catalog-browse", "snmp"] });
    },
    onError: (e: Error) => setError(e.message),
  });

  const deleteMutation = useMutation({
    mutationFn: (name: string) => deleteDriverCatalogArtifact(name, "snmp"),
    onSuccess: async () => {
      setError(null);
      await queryClient.invalidateQueries({ queryKey: ["driver-catalog-artifacts", "snmp"] });
      await queryClient.invalidateQueries({ queryKey: ["driver-catalog-browse", "snmp"] });
      setModuleId(null);
      setSelected({});
    },
    onError: (e: Error) => setError(e.message),
  });

  const importMutation = useMutation({
    mutationFn: (selections: DriverPointSelection[]) =>
      importDriverCatalogPoints(devicePath, selections, "snmp"),
    onSuccess: async (result) => {
      setError(null);
      setMessage(
        t("inspector:driver.mib.importOk", {
          vars: result.createdVariables,
          mappings: result.updatedMappings,
        }),
      );
      setSelected({});
      onImported?.();
      await queryClient.invalidateQueries({ queryKey: ["variables", devicePath] });
    },
    onError: (e: Error) => setError(e.message),
  });

  const uploadProps: UploadProps = {
    accept: ".mib,.txt,.my",
    showUploadList: false,
    beforeUpload: (file) => {
      const reader = new FileReader();
      reader.onload = () => {
        const text = typeof reader.result === "string" ? reader.result : "";
        uploadMutation.mutate({ fileName: file.name, text });
      };
      reader.readAsText(file);
      return false;
    },
  };

  const selectedList = useMemo(() => Object.values(selected), [selected]);

  const toggleNode = (node: DriverCatalogNode, checked: boolean) => {
    setSelected((prev) => {
      const next = { ...prev };
      if (!checked) {
        delete next[node.nodeId];
        return next;
      }
      next[node.nodeId] = { node, index: prev[node.nodeId]?.index ?? "" };
      return next;
    });
  };

  const applySelected = () => {
    const selections: DriverPointSelection[] = selectedList.map((row) => ({
      nodeId: row.node.nodeId,
      index: row.node.nodeClass === "COLUMN" ? row.index : "",
    }));
    for (const row of selectedList) {
      if (row.node.nodeClass === "COLUMN" && !row.index.trim()) {
        setError(t("inspector:driver.mib.indexRequired", { name: row.node.displayName }));
        return;
      }
    }
    importMutation.mutate(selections);
  };

  if (!canManage) {
    return null;
  }

  return (
    <section className="driver-mib-panel" style={{ marginTop: 16 }}>
      <h4>{t("inspector:driver.mib.title")}</h4>
      <p className="hint">{t("inspector:driver.mib.subtitle")}</p>

      <Space wrap style={{ marginBottom: 8 }}>
        <Upload {...uploadProps}>
          <Button loading={uploadMutation.isPending}>{t("inspector:driver.mib.upload")}</Button>
        </Upload>
        <Button
          disabled={!moduleId}
          onClick={() => {
            setModuleId(null);
            setSelected({});
          }}
        >
          {t("inspector:driver.mib.backModules")}
        </Button>
      </Space>

      {(artifactsQuery.data?.length ?? 0) > 0 && (
        <div className="hint" style={{ marginBottom: 8 }}>
          {t("inspector:driver.mib.library")}:{" "}
          {artifactsQuery.data?.map((a) => (
            <span key={a.name} style={{ marginRight: 8 }}>
              {a.name}
              <Button
                type="link"
                size="small"
                danger
                onClick={() => deleteMutation.mutate(a.name)}
              >
                {t("common:action.delete")}
              </Button>
            </span>
          ))}
        </div>
      )}

      {!moduleId ? (
        <ul className="driver-mib-list">
          {(modulesQuery.data ?? []).map((mod) => (
            <li key={mod.nodeId}>
              <Button type="link" onClick={() => setModuleId(mod.nodeId)}>
                {mod.displayName}
              </Button>
            </li>
          ))}
          {modulesQuery.isFetched && (modulesQuery.data?.length ?? 0) === 0 && (
            <li className="hint">{t("inspector:driver.mib.empty")}</li>
          )}
        </ul>
      ) : (
        <>
          <ul className="driver-mib-list">
            {(objectsQuery.data ?? []).map((node) => {
              const checked = !!selected[node.nodeId];
              return (
                <li key={node.nodeId}>
                  <Checkbox
                    checked={checked}
                    disabled={!node.selectable}
                    onChange={(e) => toggleNode(node, e.target.checked)}
                  >
                    <strong>{node.displayName}</strong>
                    {node.oid ? ` — ${node.oid}` : ""}
                    {node.syntax ? ` (${node.syntax})` : ""}
                  </Checkbox>
                  {checked && node.nodeClass === "COLUMN" && (
                    <Input
                      size="small"
                      style={{ width: 120, marginLeft: 24 }}
                      placeholder={t("inspector:driver.mib.indexPlaceholder")}
                      value={selected[node.nodeId]?.index ?? ""}
                      onChange={(e) =>
                        setSelected((prev) => ({
                          ...prev,
                          [node.nodeId]: {
                            node,
                            index: e.target.value,
                          },
                        }))
                      }
                    />
                  )}
                  {node.description && <div className="hint">{node.description}</div>}
                </li>
              );
            })}
          </ul>
          <Button
            type="primary"
            disabled={selectedList.length === 0 || importMutation.isPending}
            onClick={applySelected}
          >
            {t("inspector:driver.mib.applyToDevice", { count: selectedList.length })}
          </Button>
        </>
      )}

      {message && <Alert type="success" showIcon message={message} style={{ marginTop: 8 }} />}
      {error && <Alert type="error" showIcon message={error} style={{ marginTop: 8 }} />}
    </section>
  );
}
