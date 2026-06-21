export interface FunctionInvokeAuditEntry {
  id: string;
  correlationId: string;
  objectPath: string;
  functionName: string;
  appId: string | null;
  success: boolean;
  errorMessage: string | null;
  invokedAt: string;
}
