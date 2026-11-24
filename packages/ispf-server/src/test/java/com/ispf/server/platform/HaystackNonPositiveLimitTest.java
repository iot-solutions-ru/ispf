package com.ispf.server.platform;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HaystackNonPositiveLimitTest {

    @Test
    void zeroLimitReturnsAnEmptyPage() {
        Map<String, Object> search = export().searchByTags(null, "root.platform", List.of("point"), "point", 0);
        Map<String, Object> query = query().query(null, "point", "root.platform", "point", 0, 0);

        assertEquals(0, search.get("limit"));
        assertEquals(0, search.get("count"));
        assertTrue(((List<?>) search.get("matches")).isEmpty());
        assertEquals(0, query.get("limit"));
        assertEquals(0, query.get("offset"));
        assertEquals(0, query.get("count"));
        assertTrue(((List<?>) query.get("matches")).isEmpty());
    }

    @Test
    void negativeLimitAndOffsetAreRejected() {
        ResponseStatusException search = assertThrows(
                ResponseStatusException.class,
                () -> export().searchByTags(null, "root.platform", List.of("point"), "point", -1)
        );
        ResponseStatusException queryLimit = assertThrows(
                ResponseStatusException.class,
                () -> query().query(null, "point", "root.platform", "point", 0, -1)
        );
        ResponseStatusException queryOffset = assertThrows(
                ResponseStatusException.class,
                () -> query().query(null, "point", "root.platform", "point", -1, 5)
        );

        assertEquals(HttpStatus.BAD_REQUEST, search.getStatusCode());
        assertEquals("limit must not be negative", search.getReason());
        assertEquals("limit must not be negative", queryLimit.getReason());
        assertEquals("offset must not be negative", queryOffset.getReason());
    }

    private static HaystackExportService export() {
        return new HaystackExportService(null, null, null, null);
    }

    private static HaystackQueryService query() {
        return new HaystackQueryService(export(), null, null, null);
    }
}
