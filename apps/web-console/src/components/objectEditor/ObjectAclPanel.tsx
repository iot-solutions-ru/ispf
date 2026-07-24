import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Alert, Button, Input, Select, Space, Table, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useEffect, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { fetchObjectAcl, saveObjectAcl, type ObjectAclEntry } from "../../api/objectAcl";

interface ObjectAclPanelProps {
  objectPath: string;
  canManage: boolean;
}

const EMPTY_ENTRY: ObjectAclEntry = {
  principalType: "ROLE",
  principalId: "operator",
  permission: "READ",
};

export default function ObjectAclPanel({ objectPath, canManage }: ObjectAclPanelProps) {
  const { t } = useTranslation(["security", "common"]);
  const queryClient = useQueryClient();
  const [entries, setEntries] = useState<ObjectAclEntry[]>([]);
  const dirtyRef = useRef(false);
  const loadedPathRef = useRef<string | null>(null);

  const aclQuery = useQuery({
    queryKey: ["object-acl", objectPath],
    queryFn: () => fetchObjectAcl(objectPath),
  });

  useEffect(() => {
    if (aclQuery.data && (!dirtyRef.current || loadedPathRef.current !== objectPath)) {
      setEntries(aclQuery.data);
      dirtyRef.current = false;
      loadedPathRef.current = objectPath;
    }
  }, [aclQuery.data, objectPath]);

  const saveMutation = useMutation({
    mutationFn: () => saveObjectAcl(objectPath, entries),
    onSuccess: () => {
      dirtyRef.current = false;
      queryClient.invalidateQueries({ queryKey: ["object-acl", objectPath] });
    },
  });

  const updateEntry = (index: number, patch: Partial<ObjectAclEntry>) => {
    dirtyRef.current = true;
    setEntries((current) =>
      current.map((entry, entryIndex) => (entryIndex === index ? { ...entry, ...patch } : entry))
    );
  };

  const removeEntry = (index: number) => {
    dirtyRef.current = true;
    setEntries((current) => current.filter((_, entryIndex) => entryIndex !== index));
  };

  const columns: ColumnsType<ObjectAclEntry> = [
    {
      title: t("acl.column.type"),
      dataIndex: "principalType",
      key: "principalType",
      render: (principalType: ObjectAclEntry["principalType"], _entry, index) => (
        <Select
          value={principalType}
          disabled={!canManage}
          onChange={(value) => updateEntry(index, { principalType: value })}
          options={[
            { value: "ROLE", label: "ROLE" },
            { value: "USER", label: "USER" },
          ]}
          style={{ minWidth: 120 }}
        />
      ),
    },
    {
      title: t("acl.column.principal"),
      dataIndex: "principalId",
      key: "principalId",
      render: (principalId: string, _entry, index) => (
        <Input
          value={principalId}
          disabled={!canManage}
          onChange={(event) => updateEntry(index, { principalId: event.target.value })}
        />
      ),
    },
    {
      title: t("acl.column.permission"),
      dataIndex: "permission",
      key: "permission",
      render: (permission: ObjectAclEntry["permission"], _entry, index) => (
        <Select
          value={permission}
          disabled={!canManage}
          onChange={(value) => updateEntry(index, { permission: value })}
          options={[
            { value: "READ", label: "READ" },
            { value: "WRITE", label: "WRITE" },
            { value: "INVOKE", label: "INVOKE" },
          ]}
          style={{ minWidth: 120 }}
        />
      ),
    },
    ...(canManage
      ? [
          {
            title: "",
            key: "actions",
            render: (_value: unknown, _entry: ObjectAclEntry, index: number) => (
              <Button danger onClick={() => removeEntry(index)}>
                {t("common:action.delete")}
              </Button>
            ),
          },
        ]
      : []),
  ];

  return (
    <section className="security-users-panel">
      <Space orientation="vertical" size="middle" style={{ width: "100%" }}>
      <header className="security-users-header" style={{ display: "flex", justifyContent: "space-between", gap: 12 }}>
        <div>
          <Typography.Title level={3} style={{ margin: 0 }}>
            {t("acl.title")}
          </Typography.Title>
          <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>
            {t("acl.subtitle", { path: objectPath })}
          </Typography.Paragraph>
        </div>
        {canManage && (
          <Button onClick={() => { dirtyRef.current = true; setEntries((current) => [...current, { ...EMPTY_ENTRY }]); }}>
            {t("acl.addRule")}
          </Button>
        )}
      </header>

      {aclQuery.isLoading && <Typography.Text type="secondary">{t("common:action.loading")}</Typography.Text>}
      {aclQuery.error && <Alert type="error" showIcon message={String(aclQuery.error)} />}

      {entries.length === 0 && !aclQuery.isLoading && (
        <Typography.Text type="secondary">{t("acl.empty")}</Typography.Text>
      )}

      {entries.length > 0 && (
        <Table<ObjectAclEntry>
          size="small"
          rowKey={(entry, index) => `${entry.principalType}-${entry.principalId}-${entry.permission}-${index}`}
          columns={columns}
          dataSource={entries}
          pagination={false}
        />
      )}

      {canManage && (
        <Space className="operator-apps-actions" wrap>
          <Button
            type="primary"
            disabled={saveMutation.isPending}
            loading={saveMutation.isPending}
            onClick={() => saveMutation.mutate()}
          >
            {saveMutation.isPending ? t("common:action.saving") : t("acl.save")}
          </Button>
          {saveMutation.error && <Alert type="error" showIcon message={String(saveMutation.error)} />}
        </Space>
      )}
      </Space>
    </section>
  );
}
