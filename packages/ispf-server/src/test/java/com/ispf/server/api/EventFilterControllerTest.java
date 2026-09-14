package com.ispf.server.api;

import com.ispf.server.eventfilter.EventFilterObjectService;
import com.ispf.server.eventfilter.EventFilterObjectService.EventFilterDefinition;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class EventFilterControllerTest {

    @Autowired
    private EventFilterController eventFilterController;

    @Autowired
    private EventFilterObjectService eventFilterObjectService;

    @Test
    @Transactional
    void crudEventFiltersViaRestShape() {
        eventFilterObjectService.ensureCatalog();
        Authentication admin = adminAuth();

        EventFilterDefinition created = eventFilterController.create(new EventFilterController.SaveEventFilterRequest(
                "ops-critical",
                "Ops critical",
                "Critical operator feed",
                "alarm*",
                "root.platform.**",
                80L,
                100L,
                60000L,
                "",
                true
        ), admin);

        assertThat(created.path()).startsWith(EventFilterObjectService.EVENT_FILTERS_ROOT + ".");
        assertThat(created.filterId()).isEqualTo("ops-critical");

        List<EventFilterDefinition> listed = eventFilterController.list(admin);
        assertThat(listed).anyMatch(filter -> "ops-critical".equals(filter.filterId()));

        EventFilterDefinition fetched = eventFilterController.get(created.path(), admin);
        assertThat(fetched.enabled()).isTrue();

        EventFilterDefinition updated = eventFilterController.update(
                created.path(),
                new EventFilterController.SaveEventFilterRequest(
                        created.filterId(),
                        "Ops critical (updated)",
                        created.description(),
                        "alarmRaised",
                        created.sourceObjectPathPattern(),
                        created.minSeverity(),
                        created.maxSeverity(),
                        created.timeWindowMs(),
                        created.filterExpression(),
                        false
                ),
                admin
        );
        assertThat(updated.enabled()).isFalse();

        eventFilterController.delete(created.path(), admin);
        assertThat(eventFilterController.list(admin).stream().map(EventFilterDefinition::filterId))
                .doesNotContain("ops-critical");
    }

    private static Authentication adminAuth() {
        return new UsernamePasswordAuthenticationToken(
                "admin",
                "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_admin"))
        );
    }
}
