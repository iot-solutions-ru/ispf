import { useEffect, useRef, useState } from "react";
import BpmnViewer from "bpmn-js/lib/NavigatedViewer";
import ispfModdle from "../../bpmn/ispf-moddle.json";
import { EMPTY_BPMN } from "../../bpmn/constants";
import "bpmn-js/dist/assets/diagram-js.css";
import "bpmn-js/dist/assets/bpmn-js.css";
import "bpmn-js/dist/assets/bpmn-font/css/bpmn-embedded.css";

interface BpmnDiagramViewerProps {
  xml: string;
}

export default function BpmnDiagramViewer({ xml }: BpmnDiagramViewerProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const [importError, setImportError] = useState<string | null>(null);

  useEffect(() => {
    const container = containerRef.current;
    if (!container) return;

    const viewer = new BpmnViewer({
      container,
      moddleExtensions: { ispf: ispfModdle },
    });

    let disposed = false;

    const load = async () => {
      const content = xml.trim() || EMPTY_BPMN;
      try {
        await viewer.importXML(content);
        if (disposed) return;
        setImportError(null);
        const canvas = viewer.get("canvas") as { zoom: (mode: string) => void };
        canvas.zoom("fit-viewport");
      } catch (error) {
        if (disposed) return;
        setImportError((error as Error).message);
      }
    };

    void load();

    return () => {
      disposed = true;
      viewer.destroy();
    };
  }, [xml]);

  if (importError) {
    return (
      <div className="bpmn-viewer-fallback">
        <p className="hint error">Диаграмма недоступна: {importError}</p>
        <pre className="workflow-code-block workflow-bpmn-view">{xml || EMPTY_BPMN}</pre>
      </div>
    );
  }

  return <div ref={containerRef} className="bpmn-viewer-canvas" />;
}
