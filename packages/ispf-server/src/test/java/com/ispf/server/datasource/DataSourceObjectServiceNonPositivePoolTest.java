package com.ispf.server.datasource;

import com.ispf.server.datasource.DataSourceObjectService.DataSourceWriteRequest;
import com.ispf.server.object.ObjectManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DataSourceObjectServiceNonPositivePoolTest {

    @Mock
    private ObjectManager objectManager;

    @Test
    void negativePoolIsRejectedBeforeSave() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service().create(request(-1))
        );

        assertEquals("poolSize must be positive", error.getMessage());
        verify(objectManager, never()).create(any(), any(), any(), any(), any(), any());
    }

    @Test
    void zeroPoolIsRejectedBeforeSave() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service().create(request(0))
        );

        assertEquals("poolSize must be positive", error.getMessage());
        verify(objectManager, never()).create(any(), any(), any(), any(), any(), any());
    }

    private DataSourceObjectService service() {
        return new DataSourceObjectService(objectManager, null, null, null, null, null, null);
    }

    private static DataSourceWriteRequest request(int poolSize) {
        return new DataSourceWriteRequest(
                "pool-proof",
                "pool-proof",
                "internal",
                "pool_proof",
                null,
                null,
                null,
                null,
                poolSize,
                ""
        );
    }
}
