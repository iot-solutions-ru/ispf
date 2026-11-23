package com.ispf.server.object;

import com.ispf.core.object.ObjectTree;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.server.security.acl.ObjectAccessService;
import com.ispf.server.tenant.TenantScopeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ObjectSearchServiceTest {

    @Mock
    ObjectManager objectManager;
    @Mock
    ObjectAccessService objectAccessService;
    @Mock
    TenantScopeService tenantScopeService;

    private ObjectSearchService service;
    private ObjectTree tree;
    private final UsernamePasswordAuthenticationToken auth =
            new UsernamePasswordAuthenticationToken("admin", "n/a");

    @BeforeEach
    void setUp() {
        tree = new ObjectTree();
        tree.register(new PlatformObject("platform", "root.platform", ObjectType.PLATFORM, "Platform", "", null));
        tree.register(new PlatformObject("devices", "root.platform.devices", ObjectType.DEVICES, "Devices", "", null));
        tree.register(new PlatformObject(
                "deep",
                "root.platform.devices.site-a.hidden-sensor",
                ObjectType.DEVICE,
                "Hidden Sensor",
                "deep leaf",
                null
        ));
        tree.register(new PlatformObject(
                "demo",
                "root.platform.devices.demo-sensor-01",
                ObjectType.DEVICE,
                "Demo Sensor",
                "",
                null
        ));
        service = new ObjectSearchService(objectManager, objectAccessService, tenantScopeService);
    }

    private void stubOpenTree() {
        when(objectManager.tree()).thenReturn(tree);
        when(tenantScopeService.isPathVisible(any(), any())).thenReturn(true);
        when(objectAccessService.canRead(any(), any())).thenReturn(true);
    }

    @Test
    void findsUnexpandedLeafAndIncludesAncestors() {
        stubOpenTree();

        ObjectSearchService.Result result = service.search("hidden", null, null, 50, auth);

        assertThat(result.matchCount()).isEqualTo(1);
        assertThat(result.truncated()).isFalse();
        assertThat(result.nodes().stream().map(PlatformObject::path))
                .contains(
                        "root",
                        "root.platform",
                        "root.platform.devices",
                        "root.platform.devices.site-a.hidden-sensor"
                );
    }

    @Test
    void skipsUnreadObjects() {
        stubOpenTree();
        when(objectAccessService.canRead(eq("root.platform.devices.site-a.hidden-sensor"), any())).thenReturn(false);

        ObjectSearchService.Result result = service.search("hidden", null, null, 50, auth);

        assertThat(result.matchCount()).isZero();
        assertThat(result.nodes()).isEmpty();
    }

    @Test
    void unknownTypeIsRejectedBeforeSearch() {
        assertThatThrownBy(() -> service.search("room", "NOT_A_TYPE", null, 50, auth))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> {
                    ResponseStatusException status = (ResponseStatusException) error;
                    assertThat(status.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(status.getReason()).isEqualTo("Unknown object type: NOT_A_TYPE");
                });
        verify(objectManager, never()).tree();
    }

    @Test
    void knownTypeKeepsOnlyThatType() {
        stubOpenTree();

        ObjectSearchService.Result result = service.search("sensor", "device", null, 50, auth);

        assertThat(result.matchCount()).isEqualTo(2);
        assertThat(result.nodes().stream().filter(node -> node.type() == ObjectType.DEVICE).map(PlatformObject::path))
                .contains(
                        "root.platform.devices.site-a.hidden-sensor",
                        "root.platform.devices.demo-sensor-01"
                );
    }

    @Test
    void ignoresQueriesShorterThanTwoCharacters() {
        ObjectSearchService.Result result = service.search("h", null, null, 50, auth);

        assertThat(result.matchCount()).isZero();
        assertThat(result.nodes()).isEmpty();
    }

    @Test
    void zeroLimitReturnsNoMatches() {
        ObjectSearchService.Result result = service.search("sensor", null, null, 0, auth);

        assertThat(result.matchCount()).isZero();
        assertThat(result.nodes()).isEmpty();
        assertThat(result.truncated()).isFalse();
        verify(objectManager, never()).tree();
    }

    @Test
    void negativeLimitIsRejectedBeforeSearch() {
        assertThatThrownBy(() -> service.search("sensor", null, null, -1, auth))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> {
                    ResponseStatusException status = (ResponseStatusException) error;
                    assertThat(status.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(status.getReason()).isEqualTo("limit must not be negative");
                });
        verify(objectManager, never()).tree();
    }

    @Test
    void limitOneReturnsASingleMatch() {
        stubOpenTree();

        ObjectSearchService.Result result = service.search("sensor", null, null, 1, auth);

        assertThat(result.matchCount()).isEqualTo(1);
        assertThat(result.nodes().stream().filter(node -> node.type() == ObjectType.DEVICE)).hasSize(1);
    }
}
