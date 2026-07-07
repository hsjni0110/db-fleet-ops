package com.dbfleetops.policy.application;

import com.dbfleetops.database.application.DatabaseConfigurationReaderPort;
import com.dbfleetops.database.application.DatabaseConfigurationReaderPortRegistry;
import com.dbfleetops.database.dto.DatabaseConfigurationItem;
import com.dbfleetops.policy.domain.ConfigurationEngineType;
import com.dbfleetops.policy.domain.ConfigurationSnapshot;
import com.dbfleetops.policy.domain.ConfigurationSnapshotItem;
import com.dbfleetops.policy.infra.ConfigurationSnapshotItemRepository;
import com.dbfleetops.policy.infra.ConfigurationSnapshotRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ConfigurationSnapshotService {

    private final DatabaseConfigurationReaderPortRegistry readerPortRegistry;
    private final ConfigurationSnapshotRepository snapshotRepository;
    private final ConfigurationSnapshotItemRepository snapshotItemRepository;

    public ConfigurationSnapshotService(DatabaseConfigurationReaderPortRegistry readerPortRegistry,
            ConfigurationSnapshotRepository snapshotRepository,
            ConfigurationSnapshotItemRepository snapshotItemRepository) {
        this.readerPortRegistry = readerPortRegistry;
        this.snapshotRepository = snapshotRepository;
        this.snapshotItemRepository = snapshotItemRepository;
    }

    @Transactional
    public ConfigurationSnapshot collectSnapshot(Long databaseId,
            ConfigurationEngineType engineType) {
        if (databaseId == null) {
            throw new IllegalArgumentException("databaseId is required.");
        }

        if (engineType == null) {
            throw new IllegalArgumentException("engineType is required.");
        }

        DatabaseConfigurationReaderPort reader = readerPortRegistry.getReader(engineType);

        List<DatabaseConfigurationItem> configurationItems =
                reader.collectConfiguration(databaseId);

        ConfigurationSnapshot snapshot = ConfigurationSnapshot.create(databaseId, engineType);

        ConfigurationSnapshot savedSnapshot = snapshotRepository.save(snapshot);

        List<ConfigurationSnapshotItem> snapshotItems = configurationItems.stream()
                .map(item -> ConfigurationSnapshotItem.create(savedSnapshot.getId(),
                        item.parameterName(), item.actualValue(), item.unit(), item.valueType(),
                        item.dynamic(), item.source()))
                .toList();

        snapshotItemRepository.saveAll(snapshotItems);

        return savedSnapshot;
    }

    @Transactional(readOnly = true)
    public List<ConfigurationSnapshotItem> getSnapshotItems(Long snapshotId) {
        if (snapshotId == null) {
            throw new IllegalArgumentException("snapshotId is required.");
        }

        return snapshotItemRepository.findBySnapshotIdOrderByParameterNameAsc(snapshotId);
    }
}
