package com.ispf.server.plugin.blueprint;

import com.ispf.server.plugin.blueprint.dto.BlueprintAttachmentDto;
import com.ispf.server.plugin.blueprint.dto.BlueprintDetachResultDto;
import com.ispf.server.plugin.blueprint.dto.BlueprintDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/mixin-blueprints")
public class MixinBlueprintController {

    private final TypedBlueprintFacade facade;
    private final MixinReevaluationService mixinReevaluationService;

    public MixinBlueprintController(
            @Qualifier("mixinBlueprintFacade") TypedBlueprintFacade mixinBlueprintFacade,
            MixinReevaluationService mixinReevaluationService
    ) {
        this.facade = mixinBlueprintFacade;
        this.mixinReevaluationService = mixinReevaluationService;
    }

    @GetMapping
    public List<BlueprintDto> list() {
        return facade.list();
    }

    @GetMapping("/{id}")
    public BlueprintDto get(@PathVariable String id) {
        return facade.get(id);
    }

    @GetMapping("/by-name/{name}")
    public BlueprintDto getByName(@PathVariable String name) {
        return facade.getByName(name);
    }

    @PostMapping
    public BlueprintDto create(@Valid @RequestBody TypedBlueprintFacade.CreatePayload request) {
        return facade.create(request);
    }

    @PutMapping("/{id}")
    public BlueprintDto update(@PathVariable String id, @Valid @RequestBody TypedBlueprintFacade.UpdatePayload request) {
        return facade.update(id, request);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable String id) {
        facade.delete(id);
    }

    @PostMapping("/{id}/apply")
    public BlueprintAttachmentDto apply(
            @PathVariable String id,
            @RequestParam @NotBlank String objectPath
    ) {
        return facade.apply(id, objectPath);
    }

    @PostMapping("/{id}/detach")
    public BlueprintDetachResultDto detach(
            @PathVariable String id,
            @RequestParam @NotBlank String objectPath
    ) {
        return facade.detach(id, objectPath);
    }

    @PostMapping("/{id}/reevaluate")
    public List<MixinReevaluationService.ReevaluationOutcome> reevaluate(
            @PathVariable String id,
            @RequestParam(required = false) String objectPath
    ) {
        facade.get(id);
        if (objectPath != null && !objectPath.isBlank()) {
            return mixinReevaluationService.reevaluateObject(objectPath).stream()
                    .filter(o -> id.equals(o.blueprintId()))
                    .toList();
        }
        return mixinReevaluationService.reevaluateAllFor(id);
    }
}
