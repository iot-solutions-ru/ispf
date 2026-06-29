/** SCADA mimic diagram document schema (v2, SVG symbol library). */

export type MimicRotation = 0 | 90 | 180 | 270;

export interface MimicGrid {
  size: number;
  snap: boolean;
  visible: boolean;
}

export interface MimicLayer {
  id: string;
  name: string;
  visible: boolean;
  locked?: boolean;
}

export interface MimicBinding {
  objectPath?: string;
  selectionKey?: string;
  variableName: string;
  valueField?: string;
  transform?: "bool" | "number" | "string";
}

export type MimicFormatOperator = ">" | ">=" | "<" | "<=" | "==" | "!=";

export interface MimicFormatRule {
  id: string;
  bindingKey: string;
  operator: MimicFormatOperator;
  value: string | number | boolean;
  style: Record<string, string>;
}

export interface MimicLabel {
  id: string;
  text?: string;
  bindingKey?: string;
  x: number;
  y: number;
  fontSize?: number;
}

export type MimicActionType = "setVariable" | "toggleVariable" | "invokeFunction";

export interface MimicAction {
  id: string;
  type: MimicActionType;
  objectPath?: string;
  selectionKey?: string;
  variableName?: string;
  valueField?: string;
  value?: string | number | boolean;
  functionName?: string;
  confirmMessage?: string;
}

export interface MimicElement {
  id: string;
  symbolId: string;
  layerId: string;
  x: number;
  y: number;
  rotation?: MimicRotation;
  scale?: number;
  bindings: Record<string, MimicBinding>;
  formatRules?: MimicFormatRule[];
  labels?: MimicLabel[];
  actions?: MimicAction[];
  props?: Record<string, unknown>;
}

export interface MimicConnectionEndpoint {
  elementId: string;
  port: string;
}

export interface MimicConnection {
  id: string;
  layerId: string;
  from: MimicConnectionEndpoint;
  to: MimicConnectionEndpoint;
  points: { x: number; y: number }[];
  bindings?: {
    flowing?: MimicBinding;
    alarm?: MimicBinding;
  };
  style?: {
    strokeWidth?: number;
    dash?: string;
    stroke?: string;
  };
}

/** Declarative mapping from binding values to SVG presentation. */
export type MimicSymbolBehavior =
  | { bind: string; type: "visibility"; target: string; when?: "truthy" | "falsy" }
  | { bind: string; type: "hidden"; target: string; when?: "truthy" | "falsy" }
  | { bind: string; type: "text"; target: string; format?: "string" | "number"; suffix?: string; decimals?: number }
  | { bind: string; type: "fillLevel"; target: string; maxBind?: string; inset?: number }
  | { bind: string; type: "fill"; target: string; trueColor?: string; falseColor?: string }
  | { bind: string; type: "stroke"; target: string; trueColor?: string; falseColor?: string };

export interface MimicCustomSymbol {
  id: string;
  name: string;
  svg: string;
  width: number;
  height: number;
  viewBox?: string;
  ports?: MimicPort[];
  bindingSchema?: MimicBindingSlot[];
  behaviors?: MimicSymbolBehavior[];
  /** Optional trace to a built-in palette symbol id (e.g. breaker). */
  sourceSymbolId?: string;
}

export interface ScadaMimicDocument {
  version: 2;
  width: number;
  height: number;
  background?: string;
  grid?: MimicGrid;
  layers: MimicLayer[];
  elements: MimicElement[];
  connections: MimicConnection[];
  customSymbols?: MimicCustomSymbol[];
}

export interface MimicPort {
  id: string;
  x: number;
  y: number;
  direction?: "n" | "s" | "e" | "w";
}

export type MimicBindingSlotType = "boolean" | "number" | "string" | "enum";

export interface MimicBindingSlot {
  key: string;
  labelKey: string;
  type: MimicBindingSlotType;
  optional?: boolean;
}

export interface SymbolDefinition {
  id: string;
  category: string;
  nameKey: string;
  /** When set, shown instead of i18n nameKey (custom symbols). */
  displayName?: string;
  defaultWidth: number;
  defaultHeight: number;
  ports: MimicPort[];
  bindingSchema: MimicBindingSlot[];
}

export interface SymbolRenderProps {
  width: number;
  height: number;
  values: Record<string, unknown>;
  props: Record<string, unknown>;
  styleOverrides: Record<string, string>;
  editable?: boolean;
  selected?: boolean;
  onClick?: () => void;
}
