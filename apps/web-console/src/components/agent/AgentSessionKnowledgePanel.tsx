import { Alert, Button } from "antd";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import {
  deleteAgentSessionDocument,
  fetchAgentSessionDocuments,
  uploadAgentSessionDocument,
} from "../../api/ai";

function formatBytes(bytes: number): string {
  if (bytes < 1024) {
    return `${bytes} B`;
  }
  if (bytes < 1024 * 1024) {
    return `${(bytes / 1024).toFixed(1)} KB`;
  }
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

export default function AgentSessionKnowledgePanel({ sessionId }: { sessionId: string | null }) {
  const { t } = useTranslation("ai");
  const queryClient = useQueryClient();
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [description, setDescription] = useState("");
  const [uploadError, setUploadError] = useState<string | null>(null);

  const docsQuery = useQuery({
    queryKey: ["agent-session-documents", sessionId],
    queryFn: () => fetchAgentSessionDocuments(sessionId!),
    enabled: Boolean(sessionId),
  });

  const uploadMutation = useMutation({
    mutationFn: async (file: File) => uploadAgentSessionDocument(sessionId!, file, description),
    onSuccess: () => {
      setDescription("");
      setUploadError(null);
      queryClient.invalidateQueries({ queryKey: ["agent-session-documents", sessionId] });
      if (fileInputRef.current) {
        fileInputRef.current.value = "";
      }
    },
    onError: (error) => setUploadError(String(error)),
  });

  const deleteMutation = useMutation({
    mutationFn: (docId: string) => deleteAgentSessionDocument(sessionId!, docId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["agent-session-documents", sessionId] });
    },
  });

  if (!sessionId) {
    return null;
  }

  const documents = docsQuery.data?.documents ?? [];

  return (
    <details className="ai-agent-knowledge-panel">
      <summary>
        {t("agent.documents.title")}
        {documents.length > 0 ? ` (${documents.length})` : ""}
      </summary>
      <p className="hint">{t("agent.documents.hint")}</p>
      <label className="full">
        <span>{t("agent.documents.description")}</span>
        <input
          type="text"
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          placeholder={t("agent.documents.descriptionPlaceholder")}
        />
      </label>
      <div className="ai-agent-knowledge-upload">
        <input
          ref={fileInputRef}
          type="file"
          accept=".txt,.md,.markdown,.csv,.json,.xml,.html,.htm,.yaml,.yml,.log,.rst"
          onChange={(e) => {
            const file = e.target.files?.[0];
            if (file) {
              uploadMutation.mutate(file);
            }
          }}
        />
        {uploadMutation.isPending && <span className="op-muted">{t("agent.documents.uploading")}</span>}
      </div>
      {uploadError && <Alert type="warning" showIcon message={uploadError} />}
      {documents.length === 0 ? (
        <p className="op-muted">{t("agent.documents.empty")}</p>
      ) : (
        <ul className="ai-agent-knowledge-list">
          {documents.map((doc) => (
            <li key={doc.docId}>
              <span>{doc.filename}</span>
              <span className="op-muted"> {formatBytes(doc.byteSize ?? 0)}</span>
              <Button
                size="small"
                danger
                loading={deleteMutation.isPending}
                onClick={() => deleteMutation.mutate(doc.docId)}
              >
                {t("agent.documents.delete")}
              </Button>
            </li>
          ))}
        </ul>
      )}
    </details>
  );
}
