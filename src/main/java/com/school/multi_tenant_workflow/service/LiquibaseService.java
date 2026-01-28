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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Service responsible for the physical creation of tenant databases
 * and executing Liquibase schema migrations across MySQL and PostgreSQL.
 */
@Service
public class LiquibaseService {

    private static final Logger log = LoggerFactory.getLogger(LiquibaseService.class);
    private static final String CHANGELOG_FILE = "db/changelog/master.xml";

    public void runMigration(String url, String username, String password, String driverClass) {
        try {
            String dbName = extractDatabaseName(url);
            String serverUrl = extractServerUrl(url, driverClass);

            log.info("Starting automated provisioning for: {} using driver: {}", dbName, driverClass);

            // ────────────────────────────────────────────────────────────────
            // Step 1: Automated Database Creation (The Container)
            // ────────────────────────────────────────────────────────────────
            // We connect to the "Server Root" to run CREATE DATABASE
            try (HikariDataSource rootDs = createTemporaryDataSource(serverUrl, driverClass, username, password);
                 Connection rootConn = rootDs.getConnection();
                 Statement stmt = rootConn.createStatement()) {

                if (isPostgres(driverClass)) {
                    createPostgresDatabase(rootConn, stmt, dbName);
                } else {
                    createMySQLDatabase(stmt, dbName);
                }
            }

            // ────────────────────────────────────────────────────────────────
            // Step 2: Liquibase Schema Migration (The Tables)
            // ────────────────────────────────────────────────────────────────
            // Now we connect to the newly created specific database
            try (HikariDataSource tenantDs = createTemporaryDataSource(url, driverClass, username, password);
                 Connection conn = tenantDs.getConnection()) {

                Database database = DatabaseFactory.getInstance()
                        .findCorrectDatabaseImplementation(new JdbcConnection(conn));

                Liquibase liquibase = new Liquibase(CHANGELOG_FILE, new ClassLoaderResourceAccessor(), database);

                log.info("Running Liquibase migration on: {}", url);
                liquibase.update("");
                log.info("Successfully provisioned tenant: {}", dbName);
            }

        } catch (Exception e) {
            log.error("Provisioning failed for {}: {}", url, e.getMessage());
            throw new RuntimeException("Automation Error: " + e.getMessage(), e);
        }
    }

    private void createMySQLDatabase(Statement stmt, String dbName) throws SQLException {
        // MySQL allows "IF NOT EXISTS"
        String sql = "CREATE DATABASE IF NOT EXISTS `" + dbName + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci";
        stmt.executeUpdate(sql);
        log.info("MySQL database '{}' is ready.", dbName);
    }

    private void createPostgresDatabase(Connection conn, Statement stmt, String dbName) throws SQLException {
        // PostgreSQL requires manual check as it doesn't support "IF NOT EXISTS" for databases
        ResultSet rs = conn.getMetaData().getCatalogs();
        boolean exists = false;
        while (rs.next()) {
            if (rs.getString(1).equalsIgnoreCase(dbName)) {
                exists = true;
                break;
            }
        }
        rs.close();

        if (!exists) {
            // Note: PostgreSQL does not allow CREATE DATABASE inside a transaction
            stmt.executeUpdate("CREATE DATABASE " + dbName);
            log.info("PostgreSQL database '{}' created successfully.", dbName);
        } else {
            log.info("PostgreSQL database '{}' already exists.", dbName);
        }
    }

    private HikariDataSource createTemporaryDataSource(String url, String driver, String user, String pass) {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(url);
        ds.setDriverClassName(driver);
        ds.setUsername(user);
        ds.setPassword(pass);

        // Use a tiny, short-lived pool for the automation task
        ds.setMaximumPoolSize(1);
        ds.setConnectionTimeout(10000);
        return ds;
    }

    private String extractDatabaseName(String url) {
        // Handles trailing slashes or parameters in the URL
        String path = url.split("\\?")[0];
        return path.substring(path.lastIndexOf("/") + 1);
    }

    private String extractServerUrl(String url, String driver) {
        String base = url.substring(0, url.lastIndexOf("/") + 1);
        // This is the key: Postgres needs the 'postgres' db for admin tasks
        return driver.contains("postgresql") ? base + "postgres" : base;
    }

    private boolean isPostgres(String driver) {
        return driver != null && driver.toLowerCase().contains("postgresql");
    }
}