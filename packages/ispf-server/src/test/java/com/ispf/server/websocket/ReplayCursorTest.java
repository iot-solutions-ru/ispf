package com.ispf.server.websocket;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReplayCursorTest {

    @Test
    void deltaSinceReturnsOnlyNewerItemsAndAdvancesCursor() {
        ReplayCursor cursor = new ReplayCursor(2, 16);
        cursor.offer(3, "a");
        cursor.offer(4, "b");
        cursor.offer(5, "c");

        List<ReplayCursor.ReplayItem> delta = cursor.deltaSince(2);

        assertEquals(3, delta.size());
        assertEquals(3L, delta.getFirst().seq());
        assertEquals(5L, delta.getLast().seq());
        assertEquals(5L, cursor.lastSeq());

        List<ReplayCursor.ReplayItem> gapFill = cursor.deltaSince(4);
        assertEquals(1, gapFill.size());
        assertEquals(5L, gapFill.getFirst().seq());
    }

    @Test
    void boundedBufferDropsOldestBeyondCapacity() {
        ReplayCursor cursor = new ReplayCursor(0, 2);
        cursor.offer(1, "one");
        cursor.offer(2, "two");
        cursor.offer(3, "three");

        List<ReplayCursor.ReplayItem> delta = cursor.deltaSince(0);
        assertEquals(2, delta.size());
        assertEquals(2L, delta.getFirst().seq());
        assertEquals(3L, delta.getLast().seq());
        assertTrue(cursor.deltaSince(0).stream().noneMatch(item -> item.seq() == 1L));
        assertEquals(2, cursor.deltaSince(1).size());
    }
}
