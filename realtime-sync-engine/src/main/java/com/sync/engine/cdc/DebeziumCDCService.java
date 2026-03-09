package com.sync.engine.cdc;

import io.debezium.config.Configuration;
import io.debezium.engine.ChangeEvent;
import io.debezium.engine.DebeziumEngine;
import io.debezium.engine.format.Json;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Optional Debezium CDC Service.
 * Disabled by default (debezium.enabled=false) to avoid startup failures
 * when Postgres logical replication is not configured.
 */
@Service
public class DebeziumCDCService {

    private static final Logger log = LoggerFactory.getLogger(DebeziumCDCService.class);

    private final SimpMessagingTemplate messagingTemplate;

    @Value("${debezium.enabled:false}")
    private boolean debeziumEnabled;

    @Value("${spring.datasource.url:jdbc:postgresql://localhost:5432/sync_engine}")
    private String datasourceUrl;

    @Value("${spring.datasource.username:postgres}")
    private String dbUser;

    @Value("${spring.datasource.password:postgres}")
    private String dbPassword;

    private DebeziumEngine<ChangeEvent<String, String>> engine;
    private ExecutorService executorService;

    public DebeziumCDCService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @PostConstruct
    public void init() {
        if (!debeziumEnabled) {
            log.info("Debezium CDC is disabled. Set debezium.enabled=true to enable.");
            return;
        }

        try {
            String jdbcUrl = datasourceUrl.replace("jdbc:postgresql://", "");
            String[] parts = jdbcUrl.split("/");
            String[] hostPort = parts[0].split(":");
            String host = hostPort[0];
            int port = hostPort.length > 1 ? Integer.parseInt(hostPort[1]) : 5432;
            String database = parts[1].split("\\?")[0];

            Configuration config = Configuration.create()
                    .with("connector.class", "io.debezium.connector.postgresql.PostgresConnector")
                    .with("offset.storage", "org.apache.kafka.connect.storage.MemoryOffsetBackingStore")
                    .with("offset.flush.interval.ms", "1000")
                    .with("name", "sync-engine-connector")
                    .with("database.hostname", host)
                    .with("database.port", port)
                    .with("database.user", dbUser)
                    .with("database.password", dbPassword)
                    .with("database.dbname", database)
                    .with("database.server.id", "184054")
                    .with("topic.prefix", "sync-engine")
                    .with("table.include.list", "public.documents")
                    .with("plugin.name", "pgoutput")
                    .build();

            engine = DebeziumEngine.create(Json.class)
                    .using(config.asProperties())
                    .notifying(record -> {
                        log.debug("CDC Change detected: key={}", record.key());
                        messagingTemplate.convertAndSend("/topic/cdc.changes", record.value());
                    })
                    .build();

            executorService = Executors.newSingleThreadExecutor();
            executorService.execute(engine);
            log.info("Debezium CDC engine started for database: {}", database);

        } catch (Exception e) {
            log.warn("Failed to start Debezium CDC engine: {}. CDC will be disabled.", e.getMessage());
        }
    }

    @PreDestroy
    public void stop() {
        if (engine != null) {
            try {
                engine.close();
                log.info("Debezium CDC engine stopped.");
            } catch (IOException e) {
                log.error("Error stopping Debezium: {}", e.getMessage());
            }
        }
        if (executorService != null) {
            executorService.shutdown();
        }
    }
}
