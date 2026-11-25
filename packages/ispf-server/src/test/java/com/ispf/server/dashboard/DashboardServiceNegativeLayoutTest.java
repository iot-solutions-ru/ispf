package com.ispf.server.dashboard;

import com.ispf.server.object.ObjectManager;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DashboardServiceNegativeLayoutTest {

    private static final String PATH = "root.platform.dashboards.layout-proof";

    private final ObjectManager objectManager = mock(ObjectManager.class);
    private final DashboardService service = new DashboardService(objectManager, null, new ObjectMapper(), null);

    @Test
    void negativeColumnsAreRejectedBeforeTheTreeIsRead() {
        assertRejected(
                "{\"columns\":-5,\"rowHeight\":-3,\"widgets\":[{\"x\":-4,\"y\":-2,\"w\":0,\"h\":0}]}",
                "columns must be positive"
        );
    }

    @Test
    void zeroColumnsAndRowHeightAreRejected() {
        assertRejected(
                "{\"columns\":0,\"rowHeight\":0,\"widgets\":[]}",
                "columns must be positive"
        );
    }

    @Test
    void zeroRowHeightIsRejected() {
        assertRejected(
                "{\"columns\":12,\"rowHeight\":0,\"widgets\":[]}",
                "rowHeight must be positive"
        );
    }

    @Test
    void negativeWidgetCoordinatesAndNonPositiveSizeAreRejected() {
        assertRejected(
                "{\"columns\":12,\"rowHeight\":8,\"widgets\":[{\"x\":-4,\"y\":0,\"w\":4,\"h\":2}]}",
                "x must not be negative"
        );
        assertRejected(
                "{\"columns\":12,\"rowHeight\":8,\"widgets\":[{\"x\":0,\"y\":-2,\"w\":4,\"h\":2}]}",
                "y must not be negative"
        );
        assertRejected(
                "{\"columns\":12,\"rowHeight\":8,\"widgets\":[{\"x\":0,\"y\":0,\"w\":0,\"h\":2}]}",
                "w must be positive"
        );
        assertRejected(
                "{\"columns\":12,\"rowHeight\":8,\"widgets\":[{\"x\":0,\"y\":0,\"w\":4,\"h\":0}]}",
                "h must be positive"
        );
    }

    @Test
    void negativeWidgetRefreshIntervalIsRejected() {
        assertRejected(
                "{\"columns\":12,\"rowHeight\":8,\"widgets\":[{\"x\":0,\"y\":0,\"w\":4,\"h\":2,\"refreshIntervalMs\":-3}]}",
                "refreshIntervalMs must not be negative"
        );
    }

    @Test
    void nonPositiveMinimumSizeIsRejected() {
        assertRejected(
                "{\"columns\":12,\"rowHeight\":8,\"widgets\":[{\"x\":0,\"y\":0,\"w\":4,\"h\":2,\"minW\":-1}]}",
                "minW must be positive"
        );
        assertRejected(
                "{\"columns\":12,\"rowHeight\":8,\"widgets\":[{\"x\":0,\"y\":0,\"w\":4,\"h\":2,\"minH\":0}]}",
                "minH must be positive"
        );
    }

    @Test
    void negativeMarginIsRejected() {
        assertRejected(
                "{\"columns\":12,\"rowHeight\":8,\"margin\":[-1,-1],\"widgets\":[]}",
                "margin must not be negative"
        );
        assertRejected(
                "{\"columns\":12,\"rowHeight\":8,\"widgets\":[{\"x\":0,\"y\":0,\"w\":4,\"h\":2,\"margin\":[-1,-1]}]}",
                "margin must not be negative"
        );
    }

    @Test
    void positiveLayoutReachesTheTree() {
        when(objectManager.require(PATH)).thenThrow(new IllegalStateException("tree"));
        assertThrows(
                IllegalStateException.class,
                () -> service.saveLayout(
                        PATH,
                        "{\"columns\":12,\"rowHeight\":8,\"margin\":[4,4],"
                                + "\"widgets\":[{\"x\":0,\"y\":0,\"w\":4,\"h\":2,\"minW\":1,\"minH\":1,\"refreshIntervalMs\":0}]}"
                )
        );
    }

    private void assertRejected(String layoutJson, String message) {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> service.saveLayout(PATH, layoutJson)
        );
        assertEquals(message, ex.getMessage());
        verify(objectManager, never()).require(PATH);
    }
}
