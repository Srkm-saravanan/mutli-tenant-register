package com.school.multi_tenant_workflow.service;

import com.zaxxer.hikari.HikariDataSource;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.LiquibaseException;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

@Service
public class LiquibaseService {

    private static final Logger log = LoggerFactory.getLogger(LiquibaseService.class);

    private static final String CHANGELOG_FILE = "db/changelog/master.xml";

    /**
     * Provisions a new tenant database:
     *  1. Creates the database if it doesn't exist
     *  2. Applies Liquibase migrations (changelog)
     *
     * @param url        JDBC URL including database name (e.g. jdbc:mysql://localhost:3306/tenant_school_a)
     * @param username   DB user (must have CREATE DATABASE privilege for step 1)
     * @param password   DB password
     * @param driverClass JDBC driver class name
     */
    public void runMigration(String url, String username, String password, String driverClass) {
        if (url == null || url.trim().isEmpty()) {
            throw new IllegalArgumentException("JDBC URL cannot be empty");
        }

        // Extract base URL (without DB name) and database name
        int lastSlashIndex = url.lastIndexOf('/');
        if (lastSlashIndex == -1 || lastSlashIndex == url.length() - 1) {
            throw new IllegalArgumentException("Invalid JDBC URL format - missing database name: " + url);
        }

        String baseUrl = url.substring(0, lastSlashIndex + 1);
        String dbName = url.substring(lastSlashIndex + 1);

        log.info("Starting tenant provisioning for database: {}", dbName);

        // ────────────────────────────────────────────────────────────────
        // Step 1: Create database if not exists (connect to server root)
        // ────────────────────────────────────────────────────────────────
        HikariDataSource rootDataSource = createShortLivedDataSource(
                baseUrl + "?allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=UTC",
                driverClass, username, password);

        try (Connection rootConnection = rootDataSource.getConnection();
             Statement stmt = rootConnection.createStatement()) {

            String sql = "CREATE DATABASE IF NOT EXISTS `" + dbName + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci";
            stmt.executeUpdate(sql);
            log.info("Database '{}' checked / created successfully", dbName);

        } catch (SQLException e) {
            log.error("Failed to create database '{}'", dbName, e);
            throw new RuntimeException("Could not create database '" + dbName + "': " + e.getMessage(), e);
        } finally {
            rootDataSource.close();
        }

        // ────────────────────────────────────────────────────────────────
        // Step 2: Run Liquibase migrations on the target database
        // ────────────────────────────────────────────────────────────────
        HikariDataSource tenantDataSource = createShortLivedDataSource(
                url, driverClass, username, password);

        try (Connection connection = tenantDataSource.getConnection()) {

            Database database = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(connection));

            Liquibase liquibase = new Liquibase(
                    CHANGELOG_FILE,
                    new ClassLoaderResourceAccessor(),
                    database
            );

            log.info("Applying Liquibase changelog: {} to {}", CHANGELOG_FILE, url);
            liquibase.update("");

            log.info("Tenant database provisioning completed successfully: {}", url);

        } catch (LiquibaseException | SQLException e) {
            log.error("Liquibase migration failed for {}", url, e);
            throw new RuntimeException("Migration failed for " + url + ": " + e.getMessage(), e);
        } finally {
            tenantDataSource.close();
        }
    }

    /**
     * Creates a short-lived HikariDataSource (not pooled long-term)
     */
    private HikariDataSource createShortLivedDataSource(String jdbcUrl, String driverClass,
                                                        String username, String password) {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(jdbcUrl);
        ds.setDriverClassName(driverClass);
        ds.setUsername(username);
        ds.setPassword(password);

        // Short-lived pool settings (we close it right after use)
        ds.setMaximumPoolSize(2);
        ds.setMinimumIdle(0);
        ds.setIdleTimeout(10000);         // 10 seconds
        ds.setMaxLifetime(60000);         // 1 minute
        ds.setConnectionTimeout(8000);    // 8 seconds

        // MySQL validation query
        ds.setConnectionTestQuery("SELECT 1");

        return ds;
    }
}