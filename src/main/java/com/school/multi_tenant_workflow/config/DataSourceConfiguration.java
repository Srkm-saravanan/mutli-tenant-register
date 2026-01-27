package com.school.multi_tenant_workflow.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

@Configuration
public class DataSourceConfiguration {

    @Bean(name = "masterDataSource")
    public DataSource masterDataSource() {
        HikariDataSource ds = new HikariDataSource();
        ds.setDriverClassName("com.mysql.cj.jdbc.Driver");
        // Ensure this URL matches your local MySQL setup
        ds.setJdbcUrl("jdbc:mysql://localhost:3306/master_control_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC");
        ds.setUsername("root");
        ds.setPassword("mysql"); // <-- Change to your actual password

        ds.setMaximumPoolSize(10);
        return ds;
    }

    @Bean
    @Primary
    public DataSource dataSource(@Qualifier("masterDataSource") DataSource masterDataSource) {
        // Use the class in the same package
        TenantRoutingDataSource routingDataSource = new TenantRoutingDataSource();

        Map<Object, Object> targetDataSources = new HashMap<>();
        routingDataSource.setTargetDataSources(targetDataSources);

        // Fallback to Master DB so Hibernate doesn't crash at startup
        routingDataSource.setDefaultTargetDataSource(masterDataSource);

        routingDataSource.afterPropertiesSet();
        return routingDataSource;
    }
}