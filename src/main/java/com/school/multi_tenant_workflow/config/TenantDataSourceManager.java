package com.school.multi_tenant_workflow.config;

import com.school.multi_tenant_workflow.model.TenantConfig;
import com.school.multi_tenant_workflow.repository.TenantRepository;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class TenantDataSourceManager {

    private final Map<Object, Object> tenantDataSources = new ConcurrentHashMap<>();
    private final TenantRoutingDataSource routingDataSource;
    private final TenantRepository tenantRepository;

    public TenantDataSourceManager(TenantRoutingDataSource routingDataSource,
                                   TenantRepository tenantRepository) {
        this.routingDataSource = routingDataSource;
        this.tenantRepository = tenantRepository;
    }

    /**
     * Creates or updates a tenant connection pool.
     * Uses computeIfAbsent to ensure thread-safety during high-concurrency provisioning.
     */
    public void addTenant(TenantConfig config) {
        // If the tenant already exists, we close the old pool first to prevent memory leaks
        removeTenant(config.getName());

        DataSource dataSource = createHikariDataSource(config);
        tenantDataSources.put(config.getName(), dataSource);

        updateRoutingDataSource();

        System.out.println("✅ Tenant switchboard updated: " + config.getName());
    }

    private void updateRoutingDataSource() {
        routingDataSource.setTargetDataSources(tenantDataSources);
        routingDataSource.afterPropertiesSet();
    }

    private DataSource createHikariDataSource(TenantConfig config) {
        HikariConfig hikariConfig = new HikariConfig();

        // Basic Connection
        hikariConfig.setJdbcUrl(config.getUrl());
        hikariConfig.setUsername(config.getUsername());
        hikariConfig.setPassword(config.getPassword());
        hikariConfig.setDriverClassName(config.getDriverClass());
        hikariConfig.setPoolName("HikariPool-" + config.getName());

        // Performance Tuning
        hikariConfig.setMaximumPoolSize(10);
        hikariConfig.setMinimumIdle(2);
        hikariConfig.setIdleTimeout(300000);
        hikariConfig.setMaxLifetime(1800000);
        hikariConfig.setConnectionTimeout(10000);
        hikariConfig.setConnectionTestQuery("SELECT 1");

        // Database Specific Optimizations
        if (isMySQL(config.getDriverClass())) {
            applyMySQLOptimizations(hikariConfig);
        } else if (isPostgreSQL(config.getDriverClass())) {
            applyPostgresOptimizations(hikariConfig);
        }

        return new HikariDataSource(hikariConfig);
    }

    private void applyMySQLOptimizations(HikariConfig config) {
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.addDataSourceProperty("useServerPrepStmts", "true");
        config.addDataSourceProperty("useLocalSessionState", "true");
        config.addDataSourceProperty("rewriteBatchedStatements", "true");
        config.addDataSourceProperty("cacheResultSetMetadata", "true");
        config.addDataSourceProperty("cacheServerConfiguration", "true");
        config.addDataSourceProperty("elideSetAutoCommits", "true");
        config.addDataSourceProperty("maintainTimeStats", "false");
    }

    private void applyPostgresOptimizations(HikariConfig config) {
        config.addDataSourceProperty("reWriteBatchedInserts", "true");
        config.addDataSourceProperty("tcpKeepAlive", "true");
        config.addDataSourceProperty("assumeMinServerVersion", "9.4");
    }

    private boolean isMySQL(String driver) {
        return driver != null && driver.toLowerCase().contains("mysql");
    }

    private boolean isPostgreSQL(String driver) {
        return driver != null && driver.toLowerCase().contains("postgresql");
    }

    public boolean testConnection(TenantConfig config) {
        // Use a temporary data source for testing so we don't pollute the pool map
        try (HikariDataSource testDs = (HikariDataSource) createHikariDataSource(config);
             Connection conn = testDs.getConnection()) {
            return conn.isValid(2);
        } catch (SQLException e) {
            return false;
        }
    }

    public void removeTenant(String tenantName) {
        Object removed = tenantDataSources.remove(tenantName);
        if (removed instanceof HikariDataSource) {
            ((HikariDataSource) removed).close();
            updateRoutingDataSource();
            System.out.println("🗑️ Closed pool and removed tenant: " + tenantName);
        }
    }
}