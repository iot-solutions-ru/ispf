package com.ispf.server.plugin.blueprint;

import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/objects")
public class ObjectMixinReevaluationController {

    private final MixinReevaluationService mixinReevaluationService;

    public ObjectMixinReevaluationController(MixinReevaluationService mixinReevaluationService) {
        this.mixinReevaluationService = mixinReevaluationService;
    }

    @PostMapping("/by-path/reevaluate-mixins")
    public List<MixinReevaluationService.ReevaluationOutcome> reevaluateMixins(
            @RequestParam @NotBlank String path
    ) {
        return mixinReevaluationService.reevaluateObject(path);
    }
}
