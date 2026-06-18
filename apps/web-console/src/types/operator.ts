export interface WorkQueueItem {
  id: string;
  instanceId: string;
  workflowPath: string;
  taskNodeId: string;
  title: string;
  instructions: string;
  assigneeRole: string;
  status: "OPEN" | "CLAIMED" | "COMPLETED";
  assignee: string | null;
  createdAt: string;
  claimedAt: string | null;
  completedAt: string | null;
}
