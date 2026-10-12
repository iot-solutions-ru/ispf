package com.ispf.server.tenant;

import com.ispf.core.object.ObjectTree;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.spi.ObjectTreeReadyOrder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.health.contributor.Status;
import org.springframework.core.annotation.Order;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantPlatformCatalogBootstrapFailureTest {

    @Mock
    ObjectManager objectManager;
    @Mock
    TenantStore tenantStore;

    @Test
    void oneTenantFailureDoesNotThrowAndLeavesTheOtherCatalogued() {
        ObjectTree tree = mock(ObjectTree.class);
        when(objectManager.tree()).thenReturn(tree);
        when(tree.findByPath(anyString())).thenAnswer(invocation -> {
            String path = invocation.getArgument(0);
            if (path.contains(".bad")) {
                throw new IllegalStateException("catalog down");
            }
            return Optional.empty();
        });
        when(tenantStore.listAll()).thenReturn(List.of(tenant("bad"), tenant("good")));
        TenantPlatformCatalogBootstrap bootstrap = bootstrap();
        TenantCatalogHealthIndicator health = new TenantCatalogHealthIndicator(bootstrap);

        assertThatCode(bootstrap::backfillExistingTenants).doesNotThrowAnyException();

        verify(objectManager).create(eq(TenantPaths.TENANTS_ROOT), eq("good"), any(), any(), any(), isNull());
        assertThat(bootstrap.isContourReady()).isFalse();
        assertThat(bootstrap.backfillFailure()).contains("bad").contains("catalog down");
        assertThat(health.health().getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.health().getDetails()).containsEntry("error", bootstrap.backfillFailure());
    }

    @Test
    void noTenantsStaysReady() {
        when(tenantStore.listAll()).thenReturn(List.of());
        TenantPlatformCatalogBootstrap bootstrap = bootstrap();
        TenantCatalogHealthIndicator health = new TenantCatalogHealthIndicator(bootstrap);

        assertThatCode(bootstrap::backfillExistingTenants).doesNotThrowAnyException();

        assertThat(bootstrap.isContourReady()).isTrue();
        assertThat(bootstrap.backfillFailure()).isEmpty();
        assertThat(health.health().getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void listenerRunsWithDriversAndDoesNotPrecedeTreeReady() throws Exception {
        Method method = TenantPlatformCatalogBootstrap.class.getDeclaredMethod("backfillExistingTenants");
        Order order = method.getAnnotation(Order.class);
        assertThat(order).isNotNull();
        assertThat(order.value()).isEqualTo(ObjectTreeReadyOrder.AFTER_OBJECT_TREE_READY_ORDER);
        assertThat(order.value()).isGreaterThan(ObjectTreeReadyOrder.OBJECT_TREE_READY_ORDER);
    }

    private TenantPlatformCatalogBootstrap bootstrap() {
        return new TenantPlatformCatalogBootstrap(objectManager, tenantStore, committingManager());
    }

    private static Tenant tenant(String id) {
        Instant now = Instant.parse("2026-10-12T00:00:00Z");
        return new Tenant(id, id, true, null, null, now, now);
    }

    private static PlatformTransactionManager committingManager() {
        return new PlatformTransactionManager() {
            @Override
            public TransactionStatus getTransaction(TransactionDefinition definition) {
                return new SimpleTransactionStatus();
            }

            @Override
            public void commit(TransactionStatus status) {
            }

            @Override
            public void rollback(TransactionStatus status) {
            }
        };
    }
}
