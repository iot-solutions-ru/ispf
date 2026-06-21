import { fetchEvents, fetchFunctionInvocations } from "../api";
import type { FunctionInvokeAuditEntry } from "../types/runtime";
import type { ObjectEvent } from "../types/event";

export interface EventJournalFilters {
  objectPath?: string;
  limit?: number;
}

export interface FunctionInvokeFilters {
  objectPath?: string;
  functionName?: string;
  success?: boolean;
  limit?: number;
}

export function loadEventJournal(filters: EventJournalFilters = {}) {
  return fetchEvents(filters.objectPath, filters.limit ?? 50);
}

export function loadFunctionInvokeJournal(filters: FunctionInvokeFilters = {}) {
  return fetchFunctionInvocations(filters);
}

export type { ObjectEvent, FunctionInvokeAuditEntry };
