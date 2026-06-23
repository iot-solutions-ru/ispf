import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useMemo, useState } from "react";
import { fetchGroupMembers, fetchObjects, updateGroupMembers } from "../api";
import type { ObjectSummary } from "../types";
import { objectTreeKey } from "../utils/treeRowKey";

interface VisualGroupInspectorProps {
  path: string;
  canManage: boolean;
  allObjects: ObjectSummary[];
  onSelectPath: (path: string) => void;
  onMembersChanged?: () => void;
}

export default function VisualGroupInspector({
  path,
  canManage,
  allObjects,
  onSelectPath,
  onMembersChanged,
}: VisualGroupInspectorProps) {
  const queryClient = useQueryClient();
  const [pickerOpen, setPickerOpen] = useState(false);
  const [pickerQuery, setPickerQuery] = useState("");
  const [selectedMemberPaths, setSelectedMemberPaths] = useState<Set<string>>(new Set());

  const membersQuery = useQuery({
    queryKey: ["group-members", path],
    queryFn: () => fetchGroupMembers(path),
  });

  const allObjectsQuery = useQuery({
    queryKey: ["objects-all-picker"],
    queryFn: () => fetchObjects(undefined, true),
    enabled: pickerOpen,
  });

  const members = membersQuery.data ?? [];

  const memberSummaries = useMemo(() => {
    const byPath = new Map(allObjects.map((obj) => [obj.path, obj]));
    return members.map((member) => ({
      member,
      summary: byPath.get(member.path),
    }));
  }, [allObjects, members]);

  const pickerCandidates = useMemo(() => {
    const existing = new Set(members.map((m) => m.path));
    const q = pickerQuery.trim().toLowerCase();
    const pool = allObjectsQuery.data ?? allObjects;
    return pool
      .filter((obj) => !obj.groupRef && obj.path !== path && !existing.has(obj.path))
      .filter((obj) => {
        if (!q) {
          return true;
        }
        return obj.path.toLowerCase().includes(q) || obj.displayName.toLowerCase().includes(q);
      })
      .slice(0, 80);
  }, [allObjects, allObjectsQuery.data, members, path, pickerQuery]);

  const mutateMembers = useMutation({
    mutationFn: (args: { action: "add" | "remove" | "reorder"; paths: string[] }) =>
      updateGroupMembers(path, args.action, { paths: args.paths }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["group-members", path] });
      onMembersChanged?.();
    },
  });

  const toggleMember = (memberPath: string) => {
    setSelectedMemberPaths((current) => {
      const next = new Set(current);
      if (next.has(memberPath)) {
        next.delete(memberPath);
      } else {
        next.add(memberPath);
      }
      return next;
    });
  };

  return (
    <div className="visual-group-inspector">
      <p className="hint">
        Визуальная группа — ссылки на объекты без изменения их путей. Один объект может входить в несколько групп.
      </p>

      {canManage && (
        <div className="visual-group-toolbar">
          <button type="button" className="btn primary" onClick={() => setPickerOpen(true)}>
            Добавить объекты…
          </button>
          <button
            type="button"
            className="btn"
            disabled={selectedMemberPaths.size === 0 || mutateMembers.isPending}
            onClick={() => {
              mutateMembers.mutate({ action: "remove", paths: [...selectedMemberPaths] });
              setSelectedMemberPaths(new Set());
            }}
          >
            Убрать выбранных
          </button>
        </div>
      )}

      {membersQuery.isLoading && <p className="sidebar-msg">Загрузка участников…</p>}

      {!membersQuery.isLoading && members.length === 0 && (
        <p className="inspector-empty">Группа пуста. Добавьте объекты кнопкой выше.</p>
      )}

      <ul className="visual-group-member-list">
        {memberSummaries.map(({ member, summary }) => (
          <li
            key={objectTreeKey({
              path: member.path,
              groupRef: true,
              groupContextPath: path,
            } as ObjectSummary)}
            className={summary ? "" : "missing"}
          >
            {canManage && (
              <input
                type="checkbox"
                checked={selectedMemberPaths.has(member.path)}
                onChange={() => toggleMember(member.path)}
              />
            )}
            <button type="button" className="linkish" onClick={() => onSelectPath(member.path)}>
              {summary?.displayName ?? `(отсутствует) ${member.path}`}
            </button>
            <span className="tree-type mono">{member.path}</span>
          </li>
        ))}
      </ul>

      {pickerOpen && (
        <div className="modal-backdrop" onClick={() => setPickerOpen(false)}>
          <div className="modal visual-group-picker" onClick={(e) => e.stopPropagation()}>
            <h3>Добавить в группу</h3>
            <input
              type="search"
              placeholder="Поиск по имени или пути…"
              value={pickerQuery}
              onChange={(e) => setPickerQuery(e.target.value)}
              autoFocus
            />
            <ul className="visual-group-picker-list">
              {pickerCandidates.map((obj) => (
                <li key={obj.path}>
                  <button
                    type="button"
                    className="linkish"
                    onClick={() => {
                      mutateMembers.mutate(
                        { action: "add", paths: [obj.path] },
                        { onSuccess: () => setPickerOpen(false) },
                      );
                    }}
                  >
                    {obj.displayName}
                  </button>
                  <span className="tree-type mono">{obj.path}</span>
                </li>
              ))}
            </ul>
            {pickerCandidates.length === 0 && <p className="hint">Нет подходящих объектов</p>}
            <div className="modal-actions">
              <button type="button" className="btn" onClick={() => setPickerOpen(false)}>
                Закрыть
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
