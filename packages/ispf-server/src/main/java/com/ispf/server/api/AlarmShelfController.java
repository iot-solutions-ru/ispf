package com.ispf.server.api;

import com.ispf.server.alert.AlarmShelf;
import com.ispf.server.alert.AlarmShelfService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/alarm-shelves")
public class AlarmShelfController {

    private final AlarmShelfService alarmShelfService;

    public AlarmShelfController(AlarmShelfService alarmShelfService) {
        this.alarmShelfService = alarmShelfService;
    }

    @GetMapping
    public List<AlarmShelf> listActive() {
        return alarmShelfService.listActive();
    }

    @PostMapping
    public AlarmShelf shelve(@RequestBody AlarmShelfService.ShelveAlarmRequest request) {
        return alarmShelfService.shelve(request);
    }

    @DeleteMapping("/{id}")
    public void unshelve(@PathVariable String id) {
        alarmShelfService.unshelve(id);
    }
}
