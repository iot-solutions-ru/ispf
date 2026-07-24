import { Alert } from "antd";
import { useMemo } from "react";
import { useTranslation } from "react-i18next";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { fetchMimic, saveMimicDiagram, saveMimicTitle } from "../../api";
import ScadaMimicEditor from "./ScadaMimicEditor";
import { usePublishAdminFocus } from "../../hooks/usePublishAdminFocus";
import type { AdminClientFocus } from "../../context/AdminFocusContext";

interface MimicEditorPanelProps {
  path: string;
  title?: string;
  onClose: () => void;
}

function mimicStats(diagramJson: string | undefined): {
  elementCount: number;
  connectionCount: number;
  canvas: string;
} {
  try {
    const parsed = JSON.parse(diagramJson || "{}") as {
      elements?: unknown[];
      connections?: unknown[];
      width?: number;
      height?: number;
    };
    const width = typeof parsed.width === "number" ? parsed.width : 1600;
    const height = typeof parsed.height === "number" ? parsed.height : 900;
    return {
      elementCount: Array.isArray(parsed.elements) ? parsed.elements.length : 0,
      connectionCount: Array.isArray(parsed.connections) ? parsed.connections.length : 0,
      canvas: `${width}x${height}`,
    };
  } catch {
    return { elementCount: 0, connectionCount: 0, canvas: "1600x900" };
  }
}

export default function MimicEditorPanel({ path, title, onClose }: MimicEditorPanelProps) {
  const { t } = useTranslation("scada");
  const queryClient = useQueryClient();
  const mimicQuery = useQuery({
    queryKey: ["mimic", path],
    queryFn: () => fetchMimic(path),
  });

  const saveMutation = useMutation({
    mutationFn: (diagramJson: string) => saveMimicDiagram(path, diagramJson),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["mimic", path] });
    },
  });

  const stats = useMemo(
    () => mimicStats(mimicQuery.data?.diagramJson),
    [mimicQuery.data?.diagramJson]
  );

  const mimicFocus = useMemo((): AdminClientFocus => {
    return {
      surface: "mimic",
      objectPath: path,
      objectType: "MIMIC",
      priority: 80,
      detail: {
        screenTitle: `SCADA Mimic › ${title ?? path}`,
        elementCount: stats.elementCount,
        connectionCount: stats.connectionCount,
        canvas: stats.canvas,
        screenHint:
          "SCADA mimic editor — help list symbols and fill/save diagram elements on this path (no Studio modes)",
        helpIntents: ["listSymbols", "addElements", "saveDiagram", "bindTags"],
      },
    };
  }, [path, title, stats]);
  usePublishAdminFocus(`mimic-editor:${path}`, mimicFocus, Boolean(path));

  if (mimicQuery.isLoading) {
    return <Alert className="loading" type="info" showIcon message={t("editor.loading")} />;
  }

  if (mimicQuery.isError) {
    return <Alert className="error" type="error" showIcon message={(mimicQuery.error as Error).message} />;
  }

  const displayTitle = title ?? mimicQuery.data?.title ?? path;

  return (
    <ScadaMimicEditor
      diagramJson={mimicQuery.data?.diagramJson ?? "{}"}
      onSave={async (diagramJson) => {
        try {
          await saveMutation.mutateAsync(diagramJson);
          if (displayTitle !== mimicQuery.data?.title) {
            await saveMimicTitle(path, displayTitle);
          }
          onClose();
        } catch (error) {
          console.error("Failed to save mimic diagram", error);
        }
      }}
      onClose={onClose}
    />
  );
}
