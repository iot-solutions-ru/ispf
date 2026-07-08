import { useCallback, useReducer } from "react";

/** Undo/redo depth for mimic editor (BL-147 harden). */
export const MIMIC_HISTORY_LIMIT = 20;
const MAX_HISTORY = MIMIC_HISTORY_LIMIT;

interface HistoryState<T> {
  past: T[];
  present: T;
  future: T[];
}

type HistoryAction<T> =
  | { type: "SET"; payload: T; record?: boolean }
  | { type: "REPLACE"; payload: T }
  | { type: "UNDO" }
  | { type: "REDO" }
  | { type: "RESET"; payload: T };

function historyReducer<T>(state: HistoryState<T>, action: HistoryAction<T>): HistoryState<T> {
  switch (action.type) {
    case "SET": {
      if (!action.record) {
        return { ...state, present: action.payload };
      }
      const past = [...state.past, state.present].slice(-MAX_HISTORY);
      return { past, present: action.payload, future: [] };
    }
    case "REPLACE":
      return { past: [], present: action.payload, future: [] };
    case "UNDO": {
      if (state.past.length === 0) {
        return state;
      }
      const previous = state.past[state.past.length - 1];
      return {
        past: state.past.slice(0, -1),
        present: previous,
        future: [state.present, ...state.future],
      };
    }
    case "REDO": {
      if (state.future.length === 0) {
        return state;
      }
      const next = state.future[0];
      return {
        past: [...state.past, state.present],
        present: next,
        future: state.future.slice(1),
      };
    }
    case "RESET":
      return { past: [], present: action.payload, future: [] };
    default:
      return state;
  }
}

/** Undo/redo stack for mimic editor documents (BL-147). */
export function useMimicHistory<T>(initial: T) {
  const [state, dispatch] = useReducer(historyReducer<T>, {
    past: [],
    present: initial,
    future: [],
  });

  const setPresent = useCallback((next: T, recordHistory = true) => {
    dispatch({ type: "SET", payload: next, record: recordHistory });
  }, []);

  const replacePresent = useCallback((next: T) => {
    dispatch({ type: "REPLACE", payload: next });
  }, []);

  const reset = useCallback((next: T) => {
    dispatch({ type: "RESET", payload: next });
  }, []);

  const undo = useCallback(() => {
    dispatch({ type: "UNDO" });
  }, []);

  const redo = useCallback(() => {
    dispatch({ type: "REDO" });
  }, []);

  return {
    present: state.present,
    setPresent,
    replacePresent,
    reset,
    undo,
    redo,
    canUndo: state.past.length > 0,
    canRedo: state.future.length > 0,
  };
}
