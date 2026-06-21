package com.ispf.server.bootstrap;

import com.ispf.server.datasource.DataSourceObjectService;
import com.ispf.server.migration.MigrationObjectService;
import com.ispf.server.schedule.ScheduleObjectService;
import com.ispf.server.binding.SqlBindingObjectService;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Phase 14: platform catalogs (data-sources, schedules, bindings, migrations) and built-in models.
 */
@Component
public class Phase14PlatformBootstrap {

    private final Phase14ModelBootstrap phase14ModelBootstrap;
    private final DataSourceObjectService dataSourceObjectService;
    private final ScheduleObjectService scheduleObjectService;
    private final SqlBindingObjectService sqlBindingObjectService;
    private final MigrationObjectService migrationObjectService;

    public Phase14PlatformBootstrap(
            Phase14ModelBootstrap phase14ModelBootstrap,
            DataSourceObjectService dataSourceObjectService,
            ScheduleObjectService scheduleObjectService,
            SqlBindingObjectService sqlBindingObjectService,
            MigrationObjectService migrationObjectService
    ) {
        this.phase14ModelBootstrap = phase14ModelBootstrap;
        this.dataSourceObjectService = dataSourceObjectService;
        this.scheduleObjectService = scheduleObjectService;
        this.sqlBindingObjectService = sqlBindingObjectService;
        this.migrationObjectService = migrationObjectService;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(Ordered.HIGHEST_PRECEDENCE + 20)
    @Transactional
    public void onReady() {
        phase14ModelBootstrap.ensurePhase14Models();
        dataSourceObjectService.ensureCatalog();
        scheduleObjectService.ensureCatalog();
        sqlBindingObjectService.ensureCatalog();
        migrationObjectService.ensureCatalog();
    }
}
