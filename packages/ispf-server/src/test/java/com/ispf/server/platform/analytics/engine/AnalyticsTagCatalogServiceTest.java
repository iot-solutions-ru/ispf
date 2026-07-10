package com.ispf.server.platform.analytics.engine;

import com.ispf.server.config.AnalyticsProperties;
import com.ispf.server.object.BindingRulesService;
import com.ispf.server.platform.analytics.catalog.AnalyticsTagLineageService;
import com.ispf.server.platform.analytics.catalog.HistorianRuleMetaService;
import com.ispf.server.platform.analytics.pack.AnalyticsExtensionRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalyticsTagCatalogServiceTest {

    private static final String DEVICE_A = "root.platform.devices.device-a";
    private static final String DEVICE_B = "root.platform.devices.device-b";

    @Mock
    private com.ispf.server.object.ObjectManager objectManager;
    @Mock
    private BindingRulesService bindingRulesService;
    @Mock
    private AnalyticsTagLineageService lineageService;
    @Mock
    private AnalyticsScheduleRegistry scheduleRegistry;
    @Mock
    private HistorianRuleMetaService historianRuleMetaService;
    @Mock
    private AnalyticsExtensionRegistry extensionRegistry;
    @Mock
    private JdbcTemplate jdbcTemplate;

    private AnalyticsTagCatalogService catalogService;

    @BeforeEach
    void setUp() {
        catalogService = new AnalyticsTagCatalogService(
                objectManager,
                bindingRulesService,
                new AnalyticsProperties(60_000L, true, true, 60_000L, false, 60_000L, 7, 20, 3000, 600),
                lineageService,
                scheduleRegistry,
                historianRuleMetaService,
                extensionRegistry,
                jdbcTemplate
        );
        when(extensionRegistry.registeredFunctions()).thenReturn(List.of());
        when(jdbcTemplate.queryForList(any(String.class), eq(String.class), any()))
                .thenReturn(List.of(DEVICE_A, DEVICE_B));
        when(bindingRulesService.listRules(DEVICE_A)).thenReturn(List.of());
        when(bindingRulesService.listRules(DEVICE_B)).thenReturn(List.of());
    }

    @Test
    void usesBindingRulesIndexInsteadOfFullTreeScan() {
        catalogService.listAllTagDefinitions();

        verify(jdbcTemplate).queryForList(any(String.class), eq(String.class), any());
        verify(bindingRulesService).listRules(DEVICE_A);
        verify(bindingRulesService).listRules(DEVICE_B);
        verify(objectManager, org.mockito.Mockito.never()).tree();
    }

    @Test
    void cachesCompiledCatalogUntilInvalidated() {
        var first = catalogService.listAllTagDefinitions();
        var second = catalogService.listAllTagDefinitions();

        assertThat(second).isSameAs(first);
        verify(bindingRulesService, times(1)).listRules(DEVICE_A);
        verify(bindingRulesService, times(1)).listRules(DEVICE_B);

        catalogService.invalidateCatalog();
        catalogService.listAllTagDefinitions();

        verify(bindingRulesService, times(2)).listRules(DEVICE_A);
        verify(bindingRulesService, times(2)).listRules(DEVICE_B);
    }
}
