declare module "bpmn-moddle" {
  export class BpmnModdle {
    fromXML(xml: string): Promise<{ rootElement: Record<string, unknown> }>;
    toXML(
      element: Record<string, unknown>,
      options?: { format?: boolean }
    ): Promise<{ xml: string }>;
  }
}
