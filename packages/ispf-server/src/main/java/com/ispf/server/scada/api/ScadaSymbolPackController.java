package com.ispf.server.scada.api;

import com.ispf.server.scada.symbol.DropInSymbolPackLoader;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * BL-185: installed symbol packs for the mimic editor palette.
 */
@RestController
@RequestMapping("/api/v1/scada/symbol-packs")
public class ScadaSymbolPackController {

    private final DropInSymbolPackLoader symbolPackLoader;

    public ScadaSymbolPackController(DropInSymbolPackLoader symbolPackLoader) {
        this.symbolPackLoader = symbolPackLoader;
    }

    @GetMapping
    public Map<String, Object> listInstalled() {
        List<Map<String, Object>> packs = symbolPackLoader.listInstalledPacks();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "OK");
        response.put("count", packs.size());
        response.put("packs", packs);
        response.put("packsDir", symbolPackLoader.packsRoot().toString());
        return response;
    }

    @GetMapping("/{packId}")
    public Map<String, Object> getPack(@PathVariable("packId") String packId) {
        try {
            Map<String, Object> detail = symbolPackLoader.getPackDetail(packId);
            detail.put("status", "OK");
            return detail;
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage(), ex);
        }
    }
}
