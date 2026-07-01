package com.dbfleetops.database.api;

import com.dbfleetops.database.application.DatabaseInventoryService;
import com.dbfleetops.database.dto.DatabaseCreateRequest;
import com.dbfleetops.database.dto.DatabaseResponse;
import com.dbfleetops.database.dto.DatabaseUpdateRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/database-instances")
public class DatabaseInventoryController {

    private final DatabaseInventoryService databaseInventoryService;

    public DatabaseInventoryController(DatabaseInventoryService databaseInventoryService) {
        this.databaseInventoryService = databaseInventoryService;
    }

    @PostMapping
    public DatabaseResponse create(@RequestBody DatabaseCreateRequest request) {
        return databaseInventoryService.create(request);
    }

    @GetMapping
    public List<DatabaseResponse> findAll() {
        return databaseInventoryService.findAll();
    }

    @GetMapping("/{databaseId}")
    public DatabaseResponse findById(@PathVariable Long databaseId) {
        return databaseInventoryService.findById(databaseId);
    }

    @PatchMapping("/{databaseId}")
    public DatabaseResponse update(
            @PathVariable Long databaseId,
            @RequestBody DatabaseUpdateRequest request
    ) {
        return databaseInventoryService.update(databaseId, request);
    }

    @DeleteMapping("/{databaseId}")
    public void deactivate(@PathVariable Long databaseId) {
        databaseInventoryService.deactivate(databaseId);
    }
}