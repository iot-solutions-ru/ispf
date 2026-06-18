import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { claimWorkTask, completeWorkTask, fetchWorkQueue } from "../../api";
import type { WorkQueueItem } from "../../types/operator";

interface WorkQueuePanelProps {
  operatorId?: string;
}

export default function WorkQueuePanel({ operatorId = "operator" }: WorkQueuePanelProps) {
  const queryClient = useQueryClient();
  const queue = useQuery({
    queryKey: ["work-queue"],
    queryFn: () => fetchWorkQueue(30),
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
    },
  });

  const tasks = (queue.data ?? []).filter((t) => t.status !== "COMPLETED");

  return (
    <section className="work-queue-panel">
      <header className="work-queue-head">
        <h3>Очередь задач</h3>
        <span className="badge">{tasks.length}</span>
      </header>
      {queue.isLoading && <p className="hint">Загрузка…</p>}
      {queue.error && <p className="hint error">Не удалось загрузить очередь</p>}
      {tasks.length === 0 && !queue.isLoading && (
        <p className="hint">Нет открытых задач оператора</p>
      )}
      <ul className="work-queue-list">
        {tasks.map((task) => (
          <WorkQueueCard
            key={task.id}
            task={task}
            onClaim={() => claimMutation.mutate(task.id)}
            onComplete={() => completeMutation.mutate(task.id)}
            busy={claimMutation.isPending || completeMutation.isPending}
          />
        ))}
      </ul>
    </section>
  );
}

function WorkQueueCard({
  task,
  onClaim,
  onComplete,
  busy,
}: {
  task: WorkQueueItem;
  onClaim: () => void;
  onComplete: () => void;
  busy: boolean;
}) {
  return (
    <li className={`work-queue-item status-${task.status.toLowerCase()}`}>
      <div>
        <strong>{task.title}</strong>
        <p className="hint">{task.instructions || task.workflowPath}</p>
        <p className="hint">
          {task.status}
          {task.assignee ? ` · ${task.assignee}` : ""}
        </p>
      </div>
      <div className="work-queue-actions">
        {task.status === "OPEN" && (
          <button type="button" className="btn small" disabled={busy} onClick={onClaim}>
            Взять
          </button>
        )}
        {(task.status === "OPEN" || task.status === "CLAIMED") && (
          <button type="button" className="btn primary small" disabled={busy} onClick={onComplete}>
            Завершить
          </button>
        )}
      </div>
    </li>
  );
}
