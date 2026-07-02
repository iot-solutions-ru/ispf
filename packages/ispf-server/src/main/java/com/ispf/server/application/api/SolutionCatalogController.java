package com.ispf.server.application.api;

import com.ispf.server.application.bundle.SolutionCatalogService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/solutions")
public class SolutionCatalogController {

    private final SolutionCatalogService catalogService;

    public SolutionCatalogController(SolutionCatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping("/catalog")
    public Map<String, Object> catalog() {
        return catalogService.catalog();
    }

    @PostMapping("/reference/{exampleId}/install")
    public Map<String, Object> installReference(@PathVariable String exampleId) {
        try {
            return catalogService.installReferenceExample(exampleId);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage(), ex);
        }
    }
}
