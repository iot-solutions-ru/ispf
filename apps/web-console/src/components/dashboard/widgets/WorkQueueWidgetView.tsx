import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { claimWorkTask, completeWorkTask, fetchWorkQueue } from "../../../api";
import type { WorkQueueWidget } from "../../../types/dashboard";
import type { WorkQueueItem } from "../../../types/operator";
import WidgetDragHandle from "../WidgetDragHandle";

interface WorkQueueWidgetViewProps {
  widget: WorkQueueWidget;
  editable?: boolean;
}

export default function WorkQueueWidgetView({ widget, editable }: WorkQueueWidgetViewProps) {
  const operatorId = widget.operatorId ?? "operator";
  const queryClient = useQueryClient();

  const queue = useQuery({
    queryKey: ["work-queue"],
    queryFn: () => fetchWorkQueue(widget.maxItems ?? 20),
    refetchInterval: 5000,
  });

  const claimMutation = useMutation({
    mutationFn: (taskId: string) => claimWorkTask(taskId, operatorId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["work-queue"] }),
  });

  const completeMutation = useMutation({
    mutationFn: (taskId: string) => completeWorkTask(taskId, operatorId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["work-queue"] });
      queryClient.invalidateQueries({ queryKey: ["objects"] });
      queryClient.invalidateQueries({ queryKey: ["variables"] });
    },
  });

  const tasks = (queue.data ?? []).filter((t) => t.status !== "COMPLETED");
  const busy = claimMutation.isPending || completeMutation.isPending;

  return (
    <div className="dash-widget dash-widget-work-queue">
      <WidgetDragHandle visible={editable} />
      <div className="dash-widget-title">
        {widget.title}
        <span className="badge">{tasks.length}</span>
      </div>
      {queue.isLoading && <p className="hint">Загрузка…</p>}
      {tasks.length === 0 && !queue.isLoading && (
        <p className="hint">Нет открытых задач</p>
      )}
      <ul className="dash-work-queue-list">
        {tasks.map((task) => (
          <WorkQueueRow
            key={task.id}
            task={task}
            editable={editable}
            busy={busy}
            onClaim={() => claimMutation.mutate(task.id)}
            onComplete={() => completeMutation.mutate(task.id)}
          />
        ))}
      </ul>
    </div>
  );
}

function WorkQueueRow({
  task,
  editable,
  busy,
  onClaim,
  onComplete,
}: {
  task: WorkQueueItem;
  editable?: boolean;
  busy: boolean;
  onClaim: () => void;
  onComplete: () => void;
}) {
  return (
    <li className={`dash-work-queue-item status-${task.status.toLowerCase()}`}>
      <div>
        <strong>{task.title}</strong>
        <p className="hint">{task.instructions || task.workflowPath}</p>
      </div>
      {!editable && (
        <div className="dash-work-queue-actions">
          {task.status === "OPEN" && (
            <button type="button" className="btn small" disabled={busy} onClick={onClaim}>
              Взять
            </button>
          )}
          {(task.status === "OPEN" || task.status === "CLAIMED") && (
            <button type="button" className="btn primary small" disabled={busy} onClick={onComplete}>
              OK
            </button>
          )}
        </div>
      )}
    </li>
  );
}
