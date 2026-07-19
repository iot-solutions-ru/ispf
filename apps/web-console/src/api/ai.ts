import { getAuthHeaders } from "../auth/session";
import { parseApiError } from "../utils/ui/parseApiError";

export interface AiValidationResult {
  status: string;
  errors?: string[];
  warnings?: string[];
  wouldApply?: string[];
  auditId?: number;
}

export interface AiContextPackInfo {
  contextPackVersion: string;
  platformVersion?: string;
  generatedAt?: string;
  contentSha256?: string;
  exampleCount?: number;
  competitiveGapCount?: number;
  topReadinessGaps?: Array<{
    dimension?: string;
    gap?: number;
    current?: number;
    target?: number;
    phaseRef?: string;
  }>;
  livePlatform?: {
    refreshedAt?: string;
    cacheEpoch?: number;
    serverVersion?: string;
    driverCount?: number;
    applicationCount?: number;
    objectCounts?: Record<string, number>;
  };
}

export interface AiProviderStatus {
  enabled: boolean;
  providerId: string;
  available: boolean;
  model?: string;
  reason?: string;
  capabilities?: {
    vision?: boolean;
    textAttachments?: boolean;
  };
  supportedAttachmentTypes?: Array<{
    kind: string;
    mimeTypes?: string[];
    extensions?: string[];
  }>;
}

export interface AgentMessageAttachment {
  name: string;
  mimeType: string;
  contentBase64: string;
}

export interface AgentMessageAttachmentMeta {
  name?: string;
  mimeType?: string;
  byteSize?: number;
  kind?: "text" | "image";
  truncated?: boolean;
}

export interface AiGenerateResult {
  artifact: Record<string, unknown>;
  validation: AiValidationResult;
  dryRun: AiValidationResult;
  publishable: boolean;
  auditId: number;
  provider: AiProviderStatus;
}

export interface AiAgentTool {
  name: string;
  description: string;
}

export interface AiAgentStep {
  step: number;
  type: "tool" | "finish" | "error" | "guard";
  tool?: string;
  label?: string;
  arguments?: Record<string, unknown>;
  result?: Record<string, unknown>;
  summary?: string;
  error?: string;
  hint?: string;
  rawPreview?: string;
  truncated?: boolean;
  latencyMs?: number;
  promptTokens?: number;
  completionTokens?: number;
}

export interface AiAgentTurn {
  turnId: string;
  userMessage: string;
  assistantSummary: string;
  status: string;
  steps: AiAgentStep[];
  result: Record<string, unknown>;
  attachments?: AgentMessageAttachmentMeta[];
  interactionMode?: AgentInteractionMode;
  createdAt: string;
}

export interface AiAgentSessionSummary {
  sessionId: string;
  title: string;
  rootPath: string;
  createdAt: string;
  updatedAt: string;
}

export interface AiAgentSession extends AiAgentSessionSummary {
  turns: AiAgentTurn[];
  planState?: AgentPlanState;
}

export interface AiAgentRunProgress {
  running: boolean;
  completed?: boolean;
  sessionId?: string;
  userMessage?: string;
  steps?: AiAgentStep[];
  stepsCompleted?: number;
  planState?: AgentPlanState;
}

export type AgentInteractionMode = "auto" | "plan" | "execute" | "ask";

export interface AgentPlanState {
  interactionMode?: AgentInteractionMode;
  planPhase?: "none" | "planning" | "awaiting_approval" | "approved";
  planApproved?: boolean;
  plan?: Record<string, unknown>;
}

export interface AgentPlanQuestion {
  id?: string;
  text?: string;
  options?: Array<{ label?: string; value?: string }>;
}

export interface AiAgentChatResponse {
  status: string;
  sessionId: string;
  turnId?: string;
  title: string;
  message: string;
  rootPath: string;
  steps: AiAgentStep[];
  summary: string;
  result: Record<string, unknown>;
  provider: AiProviderStatus;
  contextPackVersion: string;
  stepsCompleted?: number;
  maxSteps?: number;
  running?: boolean;
  planState?: AgentPlanState;
  attachments?: AgentMessageAttachmentMeta[];
}

export interface AiAgentRunResult {
  status: string;
  sessionId?: string;
  turnId?: string;
  title?: string;
  goal?: string;
  message?: string;
  rootPath: string;
  steps: AiAgentStep[];
  summary: string;
  result: Record<string, unknown>;
  tools?: AiAgentTool[];
  provider: AiProviderStatus;
  contextPackVersion: string;
}

/** When provider responds but agent routes do not, the backend build is usually stale. */
export function agentApiUnavailableMessage(providerReachable: boolean): string {
  if (providerReachable) {
    return (
      "Сервер устарел: нет API агента (/api/v1/ai/agent/*). " +
      "Пересоберите и перезапустите ispf-server (./gradlew :packages:ispf-server:bootRun --args=\"--spring.profiles.active=local\")."
    );
  }
  return "Нет прав administrator или сессия истекла. Выйдите из консоли и войдите снова как admin.";
}

async function throwAiHttpError(response: Response, fallback: string): Promise<never> {
  const text = await response.text();
  if (response.status === 403 && fallback.includes("agent")) {
    throw new Error(
      "Нет доступа к API агента (403). " +
        "Если статус провайдера на вкладке «Настройки» загружается — пересоберите и перезапустите ispf-server. " +
        "Иначе войдите как admin или обновите сессию."
    );
  }
  throw new Error(parseApiError(text, fallback));
}

export function fetchAiContextPack(): Promise<AiContextPackInfo> {
  return fetch("/api/v1/ai/tools/context-pack", {
    headers: getAuthHeaders(),
  }).then(async (response) => {
    if (!response.ok) {
      throw new Error(parseApiError(await response.text(), `Context pack failed: ${response.status}`));
    }
    return response.json();
  });
}

export function refreshAiContextPack(): Promise<AiContextPackInfo> {
  return fetch("/api/v1/ai/tools/context-pack/refresh", {
    method: "POST",
    headers: getAuthHeaders(),
  }).then(async (response) => {
    if (!response.ok) {
      throw new Error(parseApiError(await response.text(), `Context pack refresh failed: ${response.status}`));
    }
    return response.json();
  });
}

export interface AiModelEntry {
  id: string;
  label?: string;
  providerId?: string;
}

export interface AiModelsResponse {
  models?: AiModelEntry[];
  defaultModel?: string;
}

export function fetchAiModels(): Promise<AiModelsResponse> {
  return fetch("/api/v1/ai/models", {
    headers: getAuthHeaders(),
  }).then(async (response) => {
    if (!response.ok) {
      throw new Error(parseApiError(await response.text(), `Models list failed: ${response.status}`));
    }
    return response.json();
  });
}

export function fetchAiProviderStatus(): Promise<AiProviderStatus> {
  return fetch("/api/v1/ai/provider", {
    headers: getAuthHeaders(),
  }).then(async (response) => {
    if (!response.ok) {
      throw new Error(parseApiError(await response.text(), `Provider status failed: ${response.status}`));
    }
    return response.json();
  });
}

export function validateAiBundle(appId: string, manifest: unknown): Promise<AiValidationResult> {
  return fetch("/api/v1/ai/tools/validate-bundle", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      ...getAuthHeaders(),
    },
    body: JSON.stringify({ appId, manifest }),
  }).then(async (response) => {
    if (!response.ok) {
      throw new Error(parseApiError(await response.text(), `Validate failed: ${response.status}`));
    }
    return response.json();
  });
}

export function dryRunAiDeploy(appId: string, manifest: unknown): Promise<AiValidationResult> {
  return fetch("/api/v1/ai/tools/dry-run-deploy", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      ...getAuthHeaders(),
    },
    body: JSON.stringify({ appId, manifest }),
  }).then(async (response) => {
    if (!response.ok) {
      throw new Error(parseApiError(await response.text(), `Dry-run failed: ${response.status}`));
    }
    return response.json();
  });
}

export function generateAiBundle(
  appId: string,
  prompt: string,
  baseManifest?: unknown
): Promise<AiGenerateResult> {
  return fetch("/api/v1/ai/bundles/generate", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      ...getAuthHeaders(),
    },
    body: JSON.stringify({ appId, prompt, baseManifest }),
  }).then(async (response) => {
    if (!response.ok) {
      throw new Error(parseApiError(await response.text(), `Generate failed: ${response.status}`));
    }
    return response.json();
  });
}

export function fetchAiAgentTools(): Promise<{ tools: AiAgentTool[] }> {
  return fetch("/api/v1/ai/agent/tools", {
    headers: getAuthHeaders(),
  }).then(async (response) => {
    if (!response.ok) {
      return throwAiHttpError(response, `Agent tools failed: ${response.status}`);
    }
    return response.json();
  });
}

export interface AiAgentScenario {
  id: string;
  title: string;
  prompt: string;
  assignmentType: string;
  planSteps: string[];
}

export function fetchAiAgentScenarios(): Promise<{ scenarios: AiAgentScenario[] }> {
  return fetch("/api/v1/ai/agent/scenarios", {
    headers: getAuthHeaders(),
  }).then(async (response) => {
    if (!response.ok) {
      return throwAiHttpError(response, `Agent scenarios failed: ${response.status}`);
    }
    return response.json();
  });
}

export function runAiAgent(goal: string, rootPath?: string): Promise<AiAgentRunResult> {
  return fetch("/api/v1/ai/agent/run", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      ...getAuthHeaders(),
    },
    body: JSON.stringify({ goal, rootPath: rootPath || "root" }),
  }).then(async (response) => {
    if (!response.ok) {
      return throwAiHttpError(response, `Agent run failed: ${response.status}`);
    }
    return response.json();
  });
}

export function createAgentSession(
  rootPath?: string,
  interactionMode?: AgentInteractionMode
): Promise<AiAgentSessionSummary> {
  return fetch("/api/v1/ai/agent/sessions", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      ...getAuthHeaders(),
    },
    body: JSON.stringify({
      rootPath: rootPath || "root",
      interactionMode: interactionMode ?? "auto",
    }),
  }).then(async (response) => {
    if (!response.ok) {
      return throwAiHttpError(response, `Create session failed: ${response.status}`);
    }
    return response.json();
  });
}

export function fetchAgentSession(sessionId: string): Promise<AiAgentSession> {
  return fetch(`/api/v1/ai/agent/sessions/${encodeURIComponent(sessionId)}`, {
    headers: getAuthHeaders(),
    cache: "no-store",
  }).then(async (response) => {
    if (!response.ok) {
      return throwAiHttpError(response, `Fetch session failed: ${response.status}`);
    }
    return response.json();
  });
}

export interface AiAgentAuditExport {
  sessionId: string;
  sessionTitle: string;
  actor: string;
  exportedAt: string;
  auditRows: Record<string, unknown>[];
  toolInvocations: Record<string, unknown>[];
  auditRowCount: number;
  toolInvocationCount: number;
}

export function fetchAgentAuditExport(sessionId: string): Promise<AiAgentAuditExport> {
  return fetch(`/api/v1/ai/agent/sessions/${encodeURIComponent(sessionId)}/audit`, {
    headers: getAuthHeaders(),
    cache: "no-store",
  }).then(async (response) => {
    if (!response.ok) {
      return throwAiHttpError(response, `Audit export failed: ${response.status}`);
    }
    return response.json();
  });
}

export async function downloadAgentAuditCsv(sessionId: string): Promise<void> {
  const response = await fetch(
    `/api/v1/ai/agent/sessions/${encodeURIComponent(sessionId)}/audit?format=csv`,
    { headers: getAuthHeaders(), cache: "no-store" },
  );
  if (!response.ok) {
    await throwAiHttpError(response, `Audit CSV export failed: ${response.status}`);
    return;
  }
  const blob = await response.blob();
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = `agent-audit-${sessionId}.csv`;
  link.click();
  URL.revokeObjectURL(url);
}

export interface AiAgentTraceTotals {
  latencyMs?: number;
  promptTokens?: number;
  completionTokens?: number;
  auditRowCount?: number;
}

export interface AiAgentTraceTurn {
  sessionId: string;
  turnId: string;
  status?: string;
  steps?: AiAgentStep[];
  totals?: AiAgentTraceTotals;
}

export function fetchAgentSessionTrace(
  sessionId: string,
  turnId?: string,
): Promise<AiAgentTraceTurn> {
  const query = turnId ? `?turnId=${encodeURIComponent(turnId)}` : "";
  return fetch(`/api/v1/ai/agent/sessions/${encodeURIComponent(sessionId)}/trace${query}`, {
    headers: getAuthHeaders(),
    cache: "no-store",
  }).then(async (response) => {
    if (!response.ok) {
      return throwAiHttpError(response, `Trace fetch failed: ${response.status}`);
    }
    return response.json();
  });
}

export interface AiAgentMetrics {
  days: number;
  since: string;
  turnsByStatus: Record<string, number>;
  avgStepsPerTurn: number;
  topFailingTools: Array<{ tool: string; errorCount: number }>;
  judgeFinishBlocks: number;
  promptTokensSum: number;
  completionTokensSum: number;
  latencyMsSum: number;
  promptVersions: string[];
}

export function fetchAgentMetrics(days = 7): Promise<AiAgentMetrics> {
  return fetch(`/api/v1/ai/agent/metrics?days=${days}`, {
    headers: getAuthHeaders(),
    cache: "no-store",
  }).then(async (response) => {
    if (!response.ok) {
      return throwAiHttpError(response, `Agent metrics failed: ${response.status}`);
    }
    return response.json();
  });
}

export interface AiAgentToolMetricRow {
  tool: string;
  callCount: number;
  avgLatencyMs: number;
  maxLatencyMs: number;
  promptTokens: number;
  completionTokens: number;
  errorCount: number;
  errorRate: number;
}

export interface AiAgentToolMetrics {
  days: number;
  since: string;
  toolCount: number;
  totalCalls: number;
  promptTokensSum: number;
  completionTokensSum: number;
  latencyMsSum: number;
  errorCountSum: number;
  tools: AiAgentToolMetricRow[];
}

export function fetchAgentToolMetrics(days = 7): Promise<AiAgentToolMetrics> {
  return fetch(`/api/v1/ai/agent/metrics/tools?days=${days}`, {
    headers: getAuthHeaders(),
    cache: "no-store",
  }).then(async (response) => {
    if (!response.ok) {
      return throwAiHttpError(response, `Agent tool metrics failed: ${response.status}`);
    }
    return response.json();
  });
}

export interface AiAgentSessionDocument {
  docId: string;
  filename: string;
  mimeType?: string;
  description?: string;
  byteSize?: number;
  charCount?: number;
  updatedAt?: string;
}

export function fetchAgentSessionDocuments(sessionId: string): Promise<{
  sessionId: string;
  documents: AiAgentSessionDocument[];
  count: number;
}> {
  return fetch(`/api/v1/ai/agent/sessions/${encodeURIComponent(sessionId)}/documents`, {
    headers: getAuthHeaders(),
    cache: "no-store",
  }).then(async (response) => {
    if (!response.ok) {
      return throwAiHttpError(response, `Documents list failed: ${response.status}`);
    }
    return response.json();
  });
}

export async function uploadAgentSessionDocument(
  sessionId: string,
  file: File,
  description?: string,
): Promise<{ status: string; docId: string; filename: string; byteSize: number }> {
  const form = new FormData();
  form.append("file", file);
  if (description) {
    form.append("description", description);
  }
  const response = await fetch(
    `/api/v1/ai/agent/sessions/${encodeURIComponent(sessionId)}/documents`,
    {
      method: "POST",
      headers: getAuthHeaders(),
      body: form,
    },
  );
  if (!response.ok) {
    return throwAiHttpError(response, `Document upload failed: ${response.status}`);
  }
  return response.json();
}

export function deleteAgentSessionDocument(sessionId: string, docId: string): Promise<{ status: string }> {
  return fetch(
    `/api/v1/ai/agent/sessions/${encodeURIComponent(sessionId)}/documents/${encodeURIComponent(docId)}`,
    {
      method: "DELETE",
      headers: getAuthHeaders(),
    },
  ).then(async (response) => {
    if (!response.ok) {
      return throwAiHttpError(response, `Document delete failed: ${response.status}`);
    }
    return response.json();
  });
}

export interface AiAgentAcceptedResponse {
  status: "ACCEPTED";
  sessionId: string;
  running: true;
}

export function isAgentAcceptedResponse(
  value: AiAgentChatResponse | AiAgentAcceptedResponse
): value is AiAgentAcceptedResponse {
  return value.status === "ACCEPTED" && value.running === true;
}

export function sessionTurnToChatResponse(
  session: AiAgentSession,
  turn: AiAgentTurn,
  provider: AiProviderStatus,
  contextPackVersion = ""
): AiAgentChatResponse {
  return {
    status: turn.status,
    sessionId: session.sessionId,
    turnId: turn.turnId,
    title: session.title,
    message: turn.userMessage,
    rootPath: session.rootPath,
    steps: turn.steps,
    summary: turn.assistantSummary,
    result: turn.result,
    provider,
    contextPackVersion,
    planState: session.planState,
    attachments: turn.attachments,
  };
}

export function waitUntilAgentRunIdle(sessionId: string, timeoutMs = 25_000): Promise<void> {
  const deadline = Date.now() + timeoutMs;
  return new Promise((resolve, reject) => {
    const tick = async () => {
      try {
        const progress = await fetchAgentRunProgress(sessionId);
        if (!progress.running) {
          resolve();
          return;
        }
        if (Date.now() >= deadline) {
          reject(new Error("Agent run still in progress — try Cancel or wait a moment"));
          return;
        }
        setTimeout(() => void tick(), 500);
      } catch (error) {
        reject(error);
      }
    };
    void tick();
  });
}

export type AgentClientFocus = {
  surface: string;
  objectPath?: string;
  objectType?: string;
  editorTabId?: string;
  detail?: Record<string, unknown>;
};

/** UI surface that initiated the turn — guides prompt bias (help vs build). */
export type AgentClientChannel = "studio" | "copilot";

export function sendAgentMessage(
  sessionId: string,
  message: string,
  rootPath?: string,
  interactionMode?: AgentInteractionMode,
  attachments?: AgentMessageAttachment[],
  async = true,
  clientFocus?: AgentClientFocus | null,
  clientChannel?: AgentClientChannel | null,
  uiLocale?: string | null
): Promise<AiAgentChatResponse | AiAgentAcceptedResponse> {
  const url = `/api/v1/ai/agent/sessions/${encodeURIComponent(sessionId)}/messages${
    async ? "?async=true" : ""
  }`;
  return fetch(url, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      ...getAuthHeaders(),
    },
    body: JSON.stringify({
      message,
      rootPath,
      interactionMode,
      attachments,
      ...(clientFocus ? { clientFocus } : {}),
      ...(clientChannel ? { clientChannel } : {}),
      ...(uiLocale ? { uiLocale } : {}),
    }),
  }).then(async (response) => {
    if (!response.ok) {
      return throwAiHttpError(response, `Send message failed: ${response.status}`);
    }
    return response.json();
  });
}

export interface WaitForAgentTurnOptions {
  /** Turns already in session before this message — ignore stale last turn. */
  baselineTurnCount?: number;
}

/** Ignore early running=false before the server registers the async run. */
const POST_SEND_GRACE_MS = 8_000;
/** Require running=false to stay stable before treating the run as finished. */
const RUN_IDLE_CONFIRM_MS = 400;
/** Poll session for a new turn after running=false (LLM + DB commit can take minutes). */
const TURN_FETCH_ATTEMPTS = 120;
const TURN_FETCH_DELAY_MS = 500;
const TURN_FETCH_LATE_PAUSE_MS = 10_000;
const TURN_FETCH_LATE_ATTEMPTS = 80;

const FALLBACK_PROVIDER: AiProviderStatus = {
  enabled: true,
  providerId: "unknown",
  available: true,
};

function hasTerminalProgressStep(progress: AiAgentRunProgress): boolean {
  return (progress.steps ?? []).some((step) => step.type === "finish" || step.type === "error");
}

export function isAgentTurnResultDeliveryError(error: unknown): boolean {
  const message = error instanceof Error ? error.message : String(error);
  return message.includes("without a turn result");
}

export async function fetchNewAgentTurnResponse(
  sessionId: string,
  baselineTurnCount: number
): Promise<AiAgentChatResponse | null> {
  // Session is the source of truth; provider/context must not block delivering the turn.
  const session = await fetchAgentSession(sessionId);
  if (session.turns.length <= baselineTurnCount) {
    return null;
  }
  const turn = session.turns.at(-1);
  if (!turn) {
    return null;
  }
  const [provider, contextPack] = await Promise.all([
    fetchAiProviderStatus().catch(() => FALLBACK_PROVIDER),
    fetchAiContextPack().catch(() => ({ contextPackVersion: "" } as AiContextPackInfo)),
  ]);
  return sessionTurnToChatResponse(session, turn, provider, contextPack.contextPackVersion ?? "");
}

export async function fetchNewAgentTurnWithRetry(
  sessionId: string,
  baselineTurnCount: number,
  attempts = 40,
  delayMs = 500
): Promise<AiAgentChatResponse | null> {
  for (let attempt = 0; attempt < attempts; attempt++) {
    const response = await fetchNewAgentTurnResponse(sessionId, baselineTurnCount);
    if (response) {
      return response;
    }
    if (attempt + 1 < attempts) {
      await new Promise((r) => setTimeout(r, delayMs));
    }
  }
  return null;
}

export function waitForAgentTurnCompletion(
  sessionId: string,
  onProgress?: (progress: AiAgentRunProgress) => void,
  options?: WaitForAgentTurnOptions
): Promise<AiAgentChatResponse> {
  const baselineTurnCount = options?.baselineTurnCount ?? 0;
  return new Promise((resolve, reject) => {
    let settled = false;
    let finishInFlight = false;
    let sessionPollTimer: ReturnType<typeof setInterval> | undefined;
    let unsubscribe: (() => void) | undefined;
    const waitStartedAt = Date.now();
    let sawRunning = false;
    let idleSince: number | null = null;
    let lastSteps: AiAgentStep[] = [];

    const cleanup = () => {
      if (sessionPollTimer) {
        clearInterval(sessionPollTimer);
        sessionPollTimer = undefined;
      }
      unsubscribe?.();
      unsubscribe = undefined;
    };

    const loadCompletedTurn = async (): Promise<AiAgentChatResponse | null> =>
      fetchNewAgentTurnResponse(sessionId, baselineTurnCount);

    const settle = (response: AiAgentChatResponse) => {
      if (settled) {
        return;
      }
      settled = true;
      cleanup();
      resolve(response);
    };

    const rejectTurnMissing = () => {
      if (settled) {
        return;
      }
      settled = true;
      cleanup();
      reject(new Error("Agent run completed without a turn result"));
    };

    const finish = async () => {
      if (settled || finishInFlight) {
        return;
      }
      finishInFlight = true;
      try {
        const response = await fetchNewAgentTurnWithRetry(
          sessionId,
          baselineTurnCount,
          TURN_FETCH_ATTEMPTS,
          TURN_FETCH_DELAY_MS
        );
        if (response) {
          settle(response);
          return;
        }
        await new Promise((r) => setTimeout(r, TURN_FETCH_LATE_PAUSE_MS));
        if (settled) {
          return;
        }
        const late = await fetchNewAgentTurnWithRetry(
          sessionId,
          baselineTurnCount,
          TURN_FETCH_LATE_ATTEMPTS,
          TURN_FETCH_DELAY_MS
        );
        if (late) {
          settle(late);
          return;
        }
        rejectTurnMissing();
      } catch (error) {
        if (!settled) {
          settled = true;
          cleanup();
          reject(error instanceof Error ? error : new Error(String(error)));
        }
      } finally {
        finishInFlight = false;
      }
    };

    const considerFinish = (progress: AiAgentRunProgress) => {
      if (settled) {
        return;
      }
      const running = progress.running === true;
      const completed = progress.completed === true;
      const terminal = hasTerminalProgressStep(progress);
      if (running && !terminal && !completed) {
        sawRunning = true;
        idleSince = null;
        return;
      }
      if (running) {
        sawRunning = true;
      }
      // finish/error already published, or run closed with completed snapshot — start turn fetch now
      if (completed || terminal) {
        void finish();
        return;
      }
      const now = Date.now();
      if (!sawRunning && now - waitStartedAt < POST_SEND_GRACE_MS) {
        return;
      }
      if (idleSince == null) {
        idleSince = now;
        return;
      }
      if (now - idleSince < RUN_IDLE_CONFIRM_MS) {
        return;
      }
      void finish();
    };

    sessionPollTimer = setInterval(() => {
      void loadCompletedTurn()
        .then((response) => {
          if (response) {
            settle(response);
          }
        })
        .catch(() => {
          // keep polling — transient session/provider errors must not kill settle
        });
    }, 500);

    unsubscribe = subscribeAgentRunProgress(
      sessionId,
      (progress) => {
        if (progress.steps && progress.steps.length > 0) {
          lastSteps = progress.steps;
        } else if (lastSteps.length > 0 && !progress.running) {
          // completed snapshot without steps shouldn't blank the UI
          progress = { ...progress, steps: lastSteps };
        }
        onProgress?.(progress);
        considerFinish(progress);
      },
      () => {
        // Poll errors — session turn polling continues
      }
    );
  });
}

export function fetchAgentRunProgress(sessionId: string): Promise<AiAgentRunProgress> {
  return fetch(`/api/v1/ai/agent/sessions/${encodeURIComponent(sessionId)}/progress`, {
    headers: getAuthHeaders(),
    cache: "no-store",
  }).then(async (response) => {
    if (!response.ok) {
      return throwAiHttpError(response, `Fetch progress failed: ${response.status}`);
    }
    return response.json();
  });
}

export function subscribeAgentRunProgress(
  sessionId: string,
  onProgress: (progress: AiAgentRunProgress) => void,
  onError?: (error: Event) => void
): () => void {
  // EventSource cannot send Authorization; query-token auth was removed for security.
  // Poll the Bearer-authenticated progress endpoint instead.
  let cancelled = false;
  let requestSeq = 0;

  const tick = () => {
    if (cancelled) {
      return;
    }
    const seq = ++requestSeq;
    void fetchAgentRunProgress(sessionId)
      .then((progress) => {
        // Apply latest response only — never block ticks behind a hung request.
        if (!cancelled && seq === requestSeq) {
          onProgress(progress);
        }
      })
      .catch(() => {
        if (!cancelled && seq === requestSeq) {
          onError?.(new Event("error"));
        }
      });
  };

  tick();
  const timer = setInterval(tick, 500);
  return () => {
    cancelled = true;
    clearInterval(timer);
  };
}

export function cancelAgentRun(
  sessionId: string
): Promise<{ status: string; sessionId: string; cancelRequested: boolean }> {
  return fetch(`/api/v1/ai/agent/sessions/${encodeURIComponent(sessionId)}/cancel`, {
    method: "POST",
    headers: getAuthHeaders(),
  }).then(async (response) => {
    if (!response.ok) {
      return throwAiHttpError(response, `Cancel run failed: ${response.status}`);
    }
    return response.json();
  });
}

export function deleteAgentSession(sessionId: string): Promise<{ status: string; sessionId: string }> {
  return fetch(`/api/v1/ai/agent/sessions/${encodeURIComponent(sessionId)}`, {
    method: "DELETE",
    headers: getAuthHeaders(),
  }).then(async (response) => {
    if (!response.ok) {
      return throwAiHttpError(response, `Delete session failed: ${response.status}`);
    }
    return response.json();
  });
}
