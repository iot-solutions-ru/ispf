package com.ispf.server.operator;

import com.ispf.server.ai.agent.OperatorAgentScopeService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/operator-apps/{appId}/documents")
public class OperatorAppDocumentController {

    private final OperatorAppDocumentService documentService;
    private final OperatorAgentScopeService scopeService;

    public OperatorAppDocumentController(
            OperatorAppDocumentService documentService,
            OperatorAgentScopeService scopeService
    ) {
        this.documentService = documentService;
        this.scopeService = scopeService;
    }

    @GetMapping
    public Map<String, Object> list(@PathVariable String appId) {
        ensureAppExists(appId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("appId", appId);
        result.put("count", documentService.count(appId));
        result.put("documents", documentService.listMetadata(appId, 200));
        return result;
    }

    @GetMapping("/{docId}")
    public Map<String, Object> get(@PathVariable String appId, @PathVariable String docId) {
        ensureAppExists(appId);
        OperatorAppDocumentRecord doc = documentService.get(appId, docId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("docId", doc.docId());
        result.put("filename", doc.filename());
        result.put("mimeType", doc.mimeType());
        result.put("description", doc.description());
        result.put("byteSize", doc.byteSize());
        result.put("updatedAt", doc.updatedAt().toString());
        result.put("content", doc.contentText());
        return result;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> upload(
            @PathVariable String appId,
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "description", required = false) String description
    ) throws Exception {
        ensureAppExists(appId);
        try {
            OperatorAppDocumentRecord saved = documentService.upload(appId, file, description);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("docId", saved.docId());
            result.put("filename", saved.filename());
            result.put("description", saved.description());
            result.put("byteSize", saved.byteSize());
            result.put("charCount", saved.contentText().length());
            result.put("updatedAt", saved.updatedAt().toString());
            return result;
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    @DeleteMapping("/{docId}")
    public Map<String, Object> delete(@PathVariable String appId, @PathVariable String docId) {
        ensureAppExists(appId);
        if (documentService.get(appId, docId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found");
        }
        documentService.delete(appId, docId);
        return Map.of("status", "OK", "docId", docId);
    }

    private void ensureAppExists(String appId) {
        try {
            scopeService.resolve(appId);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage(), ex);
        }
    }
}
