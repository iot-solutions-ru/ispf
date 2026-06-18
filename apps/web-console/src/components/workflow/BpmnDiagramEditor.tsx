import { useEffect, useRef, useState } from "react";
import BpmnModeler from "bpmn-js/lib/Modeler";
import ispfModdle from "../../bpmn/ispf-moddle.json";
import { EMPTY_BPMN } from "../../bpmn/constants";
import "bpmn-js/dist/assets/diagram-js.css";
import "bpmn-js/dist/assets/bpmn-js.css";
import "bpmn-js/dist/assets/bpmn-font/css/bpmn-embedded.css";

interface BpmnDiagramEditorProps {
  xml: string;
  onChange: (xml: string) => void;
}

export default function BpmnDiagramEditor({ xml, onChange }: BpmnDiagramEditorProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const modelerRef = useRef<BpmnModeler | null>(null);
  const onChangeRef = useRef(onChange);
  const skipImportRef = useRef(false);
  const [importError, setImportError] = useState<string | null>(null);

  onChangeRef.current = onChange;

  useEffect(() => {
    const container = containerRef.current;
    if (!container) return;

    const modeler = new BpmnModeler({
      container,
      keyboard: { bindTo: document },
      moddleExtensions: { ispf: ispfModdle },
    });
    modelerRef.current = modeler;

    const eventBus = modeler.get("eventBus") as {
      on: (event: string, handler: () => void) => void;
      off: (event: string, handler: () => void) => void;
    };

    let saveTimer: ReturnType<typeof setTimeout> | null = null;
    const persist = () => {
      if (saveTimer) clearTimeout(saveTimer);
      saveTimer = setTimeout(async () => {
        try {
          const result = await modeler.saveXML({ format: true });
          if (result.xml) {
            skipImportRef.current = true;
            onChangeRef.current(result.xml);
          }
        } catch (error) {
          setImportError((error as Error).message);
        }
      }, 250);
    };

    eventBus.on("commandStack.changed", persist);

    return () => {
      if (saveTimer) clearTimeout(saveTimer);
      eventBus.off("commandStack.changed", persist);
      modeler.destroy();
      modelerRef.current = null;
    };
  }, []);

  useEffect(() => {
    const modeler = modelerRef.current;
    if (!modeler) return;
    if (skipImportRef.current) {
      skipImportRef.current = false;
      return;
    }

    let cancelled = false;
    const content = xml.trim() || EMPTY_BPMN;

    void (async () => {
      try {
        await modeler.importXML(content);
        if (cancelled) return;
        setImportError(null);
        const canvas = modeler.get("canvas") as { zoom: (mode: string) => void };
        canvas.zoom("fit-viewport");
      } catch (error) {
        if (!cancelled) {
          setImportError((error as Error).message);
        }
      }
    })();

    return () => {
      cancelled = true;
    };
  }, [xml]);

  return (
    <div className="bpmn-editor-wrap">
      {importError && (
        <p className="hint error bpmn-editor-error">
          Не удалось отобразить BPMN: {importError}. Проверьте XML во вкладке «Исходник».
        </p>
      )}
      <div ref={containerRef} className="bpmn-editor-canvas" />
    </div>
  );
}
