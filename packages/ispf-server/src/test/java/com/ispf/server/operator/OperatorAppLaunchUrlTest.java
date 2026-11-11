package com.ispf.server.operator;

import com.ispf.server.application.bundle.ApplicationBundleDeployService;
import com.ispf.server.application.data.ApplicationDataStore;
import com.ispf.server.application.uipack.DropInUiPackLoader;
import com.ispf.server.application.uipack.HostedUiPackLinkEnricher;
import com.ispf.server.config.BootstrapProperties;
import com.ispf.server.tenant.TenantScopeService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OperatorAppLaunchUrlTest {

    private static final String DASHBOARD = "root.platform.dashboards.main";

    @Mock
    OperatorAppUiStore store;
    @Mock
    ApplicationDataStore applicationDataStore;
    @Mock
    ApplicationBundleDeployService bundleDeployService;
    @Mock
    OperatorAppObjectTreeService objectTreeService;
    @Mock
    BootstrapProperties bootstrapProperties;
    @Mock
    TenantScopeService tenantScopeService;
    @Mock
    DropInUiPackLoader uiPackLoader;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AtomicReference<OperatorAppUiStore.OperatorAppUiRecord> current = new AtomicReference<>();
    private OperatorAppUiService service;

    @BeforeEach
    void setUp() {
        service = new OperatorAppUiService(
                store,
                applicationDataStore,
                bundleDeployService,
                objectTreeService,
                objectMapper,
                bootstrapProperties,
                tenantScopeService,
                new HostedUiPackLinkEnricher(uiPackLoader)
        );
        var admin = new UsernamePasswordAuthenticationToken("admin", "n/a");
        SecurityContextHolder.getContext().setAuthentication(admin);
        lenient().when(tenantScopeService.resolveTenantId(admin)).thenReturn(Optional.empty());
        lenient().when(store.findByAppId("plant")).thenAnswer(invocation -> Optional.ofNullable(current.get()));
        lenient().doAnswer(invocation -> {
            current.set(invocation.getArgument(0));
            return null;
        }).when(store).upsert(any());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void saveTrimsLaunchUrlsAndKeepsOtherExtras() throws Exception {
        current.set(record("{\"reports\":[{\"path\":\"root.platform.reports.shift\",\"title\":\"Shift\"}]}"));

        Map<String, Object> ui = service.saveUi(
                "plant",
                "Plant",
                DASHBOARD,
                dashboards(),
                null,
                null,
                null,
                null,
                "  https://bridge.example/apps/plant/  ",
                "  /apps/plant/  "
        );

        assertThat(ui).containsEntry("externalSpaUrl", "https://bridge.example/apps/plant/");
        assertThat(ui).containsEntry("hostedUiUrl", "/apps/plant/");
        assertThat(ui.get("reports")).isNotNull();
        Map<String, Object> extras = extras();
        assertThat(extras).containsEntry("externalSpaUrl", "https://bridge.example/apps/plant/");
        assertThat(extras).containsEntry("hostedUiUrl", "/apps/plant/");
        assertThat(extras.get("reports")).isNotNull();
    }

    @Test
    void emptyLaunchUrlRemovesTheExtraAndNullLeavesIt() throws Exception {
        current.set(record("""
                {"externalSpaUrl":"https://keep.example/","hostedUiUrl":"/apps/plant/"}
                """));

        service.saveUi("plant", "Plant", DASHBOARD, dashboards());
        assertThat(extras())
                .containsEntry("externalSpaUrl", "https://keep.example/")
                .containsEntry("hostedUiUrl", "/apps/plant/");

        service.saveUi(
                "plant",
                "Plant",
                DASHBOARD,
                dashboards(),
                null,
                null,
                null,
                null,
                "",
                ""
        );
        assertThat(extras()).doesNotContainKeys("externalSpaUrl", "hostedUiUrl");
    }

    @Test
    void unsafeLaunchUrlsAreRejected() {
        assertRejected("javascript:alert(1)", null, "externalSpaUrl");
        assertRejected("data:text/html,hi", null, "externalSpaUrl");
        assertRejected("vbscript:msgbox(1)", null, "externalSpaUrl");
        assertRejected("/api/v1/info", null, "externalSpaUrl");
        assertRejected(null, "javascript:alert(1)", "hostedUiUrl");
    }

    @Test
    void installedPackSuppliesHostedUrlOnRead() throws Exception {
        current.set(record("""
                {"hostedUiUrl":"https://custom.example/ops","externalSpaUrl":"https://bridge.example/"}
                """));
        when(uiPackLoader.isPackInstalled("plant")).thenReturn(true);

        Map<String, Object> ui = service.getUi("plant", SecurityContextHolder.getContext().getAuthentication());

        assertThat(ui).containsEntry("hostedUiUrl", "/apps/plant/");
        assertThat(ui).containsEntry("externalSpaUrl", "https://bridge.example/");
        assertThat(extras()).containsEntry("hostedUiUrl", "https://custom.example/ops");
    }

    @Test
    void savedHostedUrlIsReturnedWhenNoPackIsInstalled() throws Exception {
        current.set(record("{\"hostedUiUrl\":\"https://custom.example/ops\"}"));

        Map<String, Object> ui = service.getUi("plant", SecurityContextHolder.getContext().getAuthentication());

        assertThat(ui).containsEntry("hostedUiUrl", "https://custom.example/ops");
    }

    private void assertRejected(String externalSpaUrl, String hostedUiUrl, String field) {
        assertThatThrownBy(() -> service.saveUi(
                "plant",
                "Plant",
                DASHBOARD,
                dashboards(),
                null,
                null,
                null,
                null,
                externalSpaUrl,
                hostedUiUrl
        ))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getReason()).contains(field);
                });
    }

    private Map<String, Object> extras() throws Exception {
        String json = current.get().uiExtrasJson();
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        return objectMapper.readValue(json, new TypeReference<>() {
        });
    }

    private static List<Map<String, String>> dashboards() {
        Map<String, String> row = new LinkedHashMap<>();
        row.put("path", DASHBOARD);
        row.put("title", "Main");
        return List.of(row);
    }

    private static OperatorAppUiStore.OperatorAppUiRecord record(String extrasJson) {
        return new OperatorAppUiStore.OperatorAppUiRecord(
                "plant",
                "Plant",
                DASHBOARD,
                "[{\"path\":\"root.platform.dashboards.main\",\"title\":\"Main\"}]",
                extrasJson,
                Instant.parse("2026-10-09T00:00:00Z")
        );
    }
}
