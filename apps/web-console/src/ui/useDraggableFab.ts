import {
  useCallback,
  useLayoutEffect,
  useRef,
  useState,
  type CSSProperties,
  type MouseEvent as ReactMouseEvent,
  type PointerEvent as ReactPointerEvent,
  type RefObject,
} from "react";

/** Shared with the hosted ui-pack script so one drag is remembered everywhere. */
export const FAB_POS_KEY = "ispf-operator-agent-pos";

const SIZE = 52;
const MARGIN = 8;

export interface FabPoint {
  x: number;
  y: number;
}

export interface Viewport {
  width: number;
  height: number;
}

export function currentViewport(): Viewport {
  if (typeof window === "undefined") {
    return { width: 1280, height: 800 };
  }
  return { width: window.innerWidth, height: window.innerHeight };
}

export function clampFabPos(pos: FabPoint, viewport: Viewport = currentViewport()): FabPoint {
  return {
    x: Math.min(Math.max(MARGIN, pos.x), Math.max(MARGIN, viewport.width - SIZE - MARGIN)),
    y: Math.min(Math.max(MARGIN, pos.y), Math.max(MARGIN, viewport.height - SIZE - MARGIN)),
  };
}

export function defaultFabPos(viewport: Viewport = currentViewport()): FabPoint {
  return clampFabPos({ x: viewport.width - 72, y: viewport.height - 72 }, viewport);
}

export function readFabPos(viewport: Viewport = currentViewport()): FabPoint {
  try {
    const raw = localStorage.getItem(FAB_POS_KEY);
    if (raw) {
      const parsed = JSON.parse(raw) as Partial<FabPoint>;
      if (typeof parsed.x === "number" && typeof parsed.y === "number") {
        return clampFabPos({ x: parsed.x, y: parsed.y }, viewport);
      }
    }
  } catch {
    /* private mode or a blocked storage */
  }
  return defaultFabPos(viewport);
}

export function persistFabPos(pos: FabPoint): void {
  try {
    localStorage.setItem(FAB_POS_KEY, JSON.stringify(clampFabPos(pos)));
  } catch {
    /* ignore */
  }
}

export function drawerStyleFor(
  pos: FabPoint,
  drawerHeight: number,
  drawerWidth: number,
  viewport: Viewport = currentViewport(),
): CSSProperties {
  const panelWidth = Math.min(420, viewport.width - 16, drawerWidth > 0 ? drawerWidth : 420);
  let left = pos.x + SIZE - panelWidth;
  if (left < 8) {
    left = 8;
  }
  if (left + panelWidth > viewport.width - 8) {
    left = Math.max(8, viewport.width - panelWidth - 8);
  }
  const height = drawerHeight > 0 ? drawerHeight : 360;
  let top = pos.y - height - 12;
  if (top < 8) {
    top = pos.y + 64;
  }
  if (top + height > viewport.height - 8) {
    top = Math.max(8, viewport.height - height - 8);
  }
  return {
    left,
    top,
    right: "auto",
    bottom: "auto",
    width: panelWidth,
  };
}

interface DragState {
  id: number;
  sx: number;
  sy: number;
  ox: number;
  oy: number;
  moved: boolean;
}

export function useDraggableFab(open: boolean): {
  buttonStyle: CSSProperties;
  drawerStyle: CSSProperties | undefined;
  drawerRef: RefObject<HTMLDivElement | null>;
  dragging: boolean;
  onPointerDown: (event: ReactPointerEvent<HTMLButtonElement>) => void;
  onPointerMove: (event: ReactPointerEvent<HTMLButtonElement>) => void;
  onPointerUp: (event: ReactPointerEvent<HTMLButtonElement>) => void;
  onPointerCancel: (event: ReactPointerEvent<HTMLButtonElement>) => void;
  consumeClick: (event: ReactMouseEvent) => boolean;
} {
  const [pos, setPos] = useState<FabPoint>(() => readFabPos());
  const [dragging, setDragging] = useState(false);
  const [drawerStyle, setDrawerStyle] = useState<CSSProperties | undefined>();
  const drawerRef = useRef<HTMLDivElement>(null);
  const dragRef = useRef<DragState | null>(null);
  const skipClick = useRef(false);
  const posRef = useRef(pos);
  posRef.current = pos;

  const placeDrawer = useCallback((next: FabPoint) => {
    const el = drawerRef.current;
    if (!el) {
      return;
    }
    setDrawerStyle(drawerStyleFor(next, el.offsetHeight, el.offsetWidth));
  }, []);

  useLayoutEffect(() => {
    if (!open) {
      return;
    }
    placeDrawer(pos);
    const el = drawerRef.current;
    if (!el || typeof ResizeObserver === "undefined") {
      return;
    }
    const observer = new ResizeObserver(() => placeDrawer(posRef.current));
    observer.observe(el);
    return () => observer.disconnect();
  }, [open, pos, placeDrawer]);

  useLayoutEffect(() => {
    const onResize = () => setPos((current) => clampFabPos(current));
    window.addEventListener("resize", onResize);
    return () => window.removeEventListener("resize", onResize);
  }, []);

  const endDrag = (event: ReactPointerEvent<HTMLButtonElement>) => {
    const drag = dragRef.current;
    if (!drag || drag.id !== event.pointerId) {
      return;
    }
    const moved = drag.moved;
    dragRef.current = null;
    setDragging(false);
    if (!moved) {
      return;
    }
    skipClick.current = true;
    const next = clampFabPos({
      x: drag.ox + (event.clientX - drag.sx),
      y: drag.oy + (event.clientY - drag.sy),
    });
    setPos(next);
    persistFabPos(next);
  };

  return {
    buttonStyle: {
      left: pos.x,
      top: pos.y,
      right: "auto",
      bottom: "auto",
    },
    drawerStyle,
    drawerRef,
    dragging,
    onPointerDown: (event) => {
      if (event.button != null && event.button !== 0) {
        return;
      }
      const origin = posRef.current;
      dragRef.current = {
        id: event.pointerId,
        sx: event.clientX,
        sy: event.clientY,
        ox: origin.x,
        oy: origin.y,
        moved: false,
      };
      try {
        event.currentTarget.setPointerCapture(event.pointerId);
      } catch {
        /* jsdom has no pointer capture */
      }
    },
    onPointerMove: (event) => {
      const drag = dragRef.current;
      if (!drag || drag.id !== event.pointerId) {
        return;
      }
      const dx = event.clientX - drag.sx;
      const dy = event.clientY - drag.sy;
      if (!drag.moved && Math.abs(dx) + Math.abs(dy) < 6) {
        return;
      }
      drag.moved = true;
      setDragging(true);
      setPos(clampFabPos({ x: drag.ox + dx, y: drag.oy + dy }));
    },
    onPointerUp: endDrag,
    onPointerCancel: endDrag,
    consumeClick: (event) => {
      if (!skipClick.current) {
        return false;
      }
      skipClick.current = false;
      event.preventDefault();
      event.stopPropagation();
      return true;
    },
  };
}
