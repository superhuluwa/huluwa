package com.huluwa.console.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huluwa.console.config.AndroidBuildProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Path;
import java.util.Properties;

/** Independent counters, nodes and artifacts for the TerminalBuddy Android app. */
@Service
public class TerminalBuddyBuildService {
    private final AndroidBuildService builds;

    public TerminalBuddyBuildService(ObjectMapper json, AndroidBuildProperties properties,
                                    @Value("${huluwa.data-dir:./data}") String dataDirectory) {
        var source = new DriverManagerDataSource();
        source.setDriverClassName("org.sqlite.JDBC");
        source.setUrl("jdbc:sqlite:" + Path.of(dataDirectory).resolve("terminalbuddy-build.db").toAbsolutePath());
        var settings = new Properties();
        settings.setProperty("busy_timeout", "10000");
        source.setConnectionProperties(settings);
        new ResourceDatabasePopulator(new ClassPathResource("terminalbuddy-build-schema.sql")).execute(source);
        var config = new AndroidBuildProperties(properties.enabled(), properties.directory().resolve("terminalbuddy"),
                properties.timeoutSeconds(), properties.retention());
        builds = new AndroidBuildService(new JdbcTemplate(source),
                new TransactionTemplate(new DataSourceTransactionManager(source)), json, config,
                "com.terminalbuddy.android", "terminalbuddy", 1000, false);
    }

    public AndroidBuildService builds() { return builds; }

    public String server(String requested) {
        if (requested != null && !requested.isBlank() && !"local".equals(requested)) return requested;
        return builds.servers().stream().findFirst().map(row -> String.valueOf(row.get("id"))).orElse("local");
    }

    @Scheduled(fixedDelay = 1000)
    public void refresh() { builds.refresh(); }
}
