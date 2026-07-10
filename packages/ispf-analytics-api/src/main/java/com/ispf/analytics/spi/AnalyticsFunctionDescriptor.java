package com.ispf.analytics.spi;

import java.util.List;
import java.util.Set;

/**
 * Immutable metadata for one analytics function exposed via SPI.
 *
 * @param id stable function identifier (unique within a pack)
 * @param displayName localized/UI-friendly function name
 * @param helper helper token used by evaluator lookup
 * @param syntax usage syntax string for docs/UI hints
 * @param parameters function parameter metadata in declaration order
 * @param tags capability tags for search/grouping
 * @param packId parent pack identifier
 */
public record AnalyticsFunctionDescriptor(
        String id,
        String displayName,
        String helper,
        String syntax,
        List<AnalyticsFunctionParameterDescriptor> parameters,
        Set<String> tags,
        String packId
) {
}
