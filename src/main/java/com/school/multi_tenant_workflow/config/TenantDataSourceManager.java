package com.school.multi_tenant_workflow.config;

import com.school.multi_tenant_workflow.model.TenantConfig;
import com.school.multi_tenant_workflow.repository.TenantRepository;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class TenantDataSourceManager {

    private final Map<Object, Object> tenantDataSources = new ConcurrentHashMap<>();
    private final TenantRoutingDataSource routingDataSource;

    public TenantDataSourceManager(TenantRoutingDataSource routingDataSource) {
        this.routingDataSource = routingDataSource;
    }

    // This creates a new connection pool for a tenant on the fly
    public void addTenant(TenantConfig config) {
        DataSource dataSource = DataSourceBuilder.create()
                .url(config.getUrl())
                .username(config.getUsername())
                .password(config.getPassword())
                .driverClassName(config.getDriverClass())
                .build();

        tenantDataSources.put(config.getName(), dataSource);

        // Tell the switchboard about the new tenant
        routingDataSource.setTargetDataSources(new HashMap<>(tenantDataSources));
        routingDataSource.afterPropertiesSet(); // Refresh the switchboard
    }
}