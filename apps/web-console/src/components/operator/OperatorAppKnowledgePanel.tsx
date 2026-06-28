import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import {
  deleteOperatorAppDocument,
  fetchOperatorAppDocuments,
  uploadOperatorAppDocument,
} from "../../api/operatorAppDocuments";

interface OperatorAppKnowledgePanelProps {
  appId: string;
  canManage: boolean;
  agentInstructions: string;
  onAgentInstructionsChange: (value: string) => void;
}

function formatBytes(bytes: number): string {
  if (bytes < 1024) {
    return `${bytes} B`;
  }
  if (bytes < 1024 * 1024) {
    return `${(bytes / 1024).toFixed(1)} KB`;
  }
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

export default function OperatorAppKnowledgePanel({
  appId,
  canManage,
  agentInstructions,
  onAgentInstructionsChange,
}: OperatorAppKnowledgePanelProps) {
  const { t } = useTranslation(["operator", "common"]);
  const queryClient = useQueryClient();
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [description, setDescription] = useState("");
  const [uploadError, setUploadError] = useState<string | null>(null);

  const docsQuery = useQuery({
    queryKey: ["operator-app-documents", appId],
    queryFn: () => fetchOperatorAppDocuments(appId),
    enabled: Boolean(appId),
  });

  const uploadMutation = useMutation({
    mutationFn: async (file: File) => uploadOperatorAppDocument(appId, file, description),
    onSuccess: () => {
      setDescription("");
      setUploadError(null);
      queryClient.invalidateQueries({ queryKey: ["operator-app-documents", appId] });
      if (fileInputRef.current) {
        fileInputRef.current.value = "";
      }
    },
    onError: (error) => setUploadError(String(error)),
  });

  const deleteMutation = useMutation({
    mutationFn: (docId: string) => deleteOperatorAppDocument(appId, docId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["operator-app-documents", appId] });
    },
  });

  const handleFileChange = (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (!file || !canManage) {
      return;
    }
    uploadMutation.mutate(file);
  };

  const documents = docsQuery.data?.documents ?? [];

  return (
    <div className="full operator-app-knowledge">
      <strong>{t("operatorApps.knowledgeTitle")}</strong>
      <p className="hint">{t("operatorApps.knowledgeHint")}</p>

      <label className="full">
        {t("operatorApps.agentInstructions")}
        <textarea
          className="operator-app-instructions"
          rows={5}
          value={agentInstructions}
          onChange={(e) => onAgentInstructionsChange(e.target.value)}
          disabled={!canManage}
          placeholder={t("operatorApps.agentInstructionsPlaceholder")}
        />
      </label>

      <div className="operator-app-documents-head">
        <strong>{t("operatorApps.documentsTitle")}</strong>
        {canManage && (
          <div className="operator-app-documents-upload">
            <input
              type="text"
              className="operator-app-doc-description"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder={t("operatorApps.documentDescriptionPlaceholder")}
            />
            <input
              ref={fileInputRef}
              type="file"
              accept=".txt,.md,.markdown,.csv,.json,.xml,.html,.htm,.yaml,.yml,.log,.rst"
              onChange={handleFileChange}
              disabled={uploadMutation.isPending}
            />
          </div>
        )}
      </div>
      <p className="hint">{t("operatorApps.documentsFormats")}</p>

      {docsQuery.isLoading && <p className="hint">{t("common:action.loading")}</p>}
      {docsQuery.error && <p className="hint error">{String(docsQuery.error)}</p>}
      {uploadError && <p className="hint error">{uploadError}</p>}
      {uploadMutation.isPending && <p className="hint">{t("operatorApps.uploadingDocument")}</p>}

      {documents.length === 0 && !docsQuery.isLoading && (
        <p className="hint">{t("operatorApps.documentsEmpty")}</p>
      )}

      {documents.length > 0 && (
        <ul className="operator-app-documents-list">
          {documents.map((doc) => (
            <li key={doc.docId} className="operator-app-document-item">
              <div className="operator-app-document-meta">
                <span className="operator-app-document-name">{doc.filename}</span>
                {doc.description && (
                  <span className="hint operator-app-document-desc">{doc.description}</span>
                )}
                <span className="hint">
                  {formatBytes(doc.byteSize)}
                  {doc.charCount > 0 ? ` · ${doc.charCount.toLocaleString()} chars` : ""}
                </span>
              </div>
              {canManage && (
                <button
                  type="button"
                  className="btn subtle small"
                  disabled={deleteMutation.isPending}
                  onClick={() => deleteMutation.mutate(doc.docId)}
                >
                  {t("common:action.delete")}
                </button>
              )}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
