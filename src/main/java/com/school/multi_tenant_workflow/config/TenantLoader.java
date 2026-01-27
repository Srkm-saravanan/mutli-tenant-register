package com.school.multi_tenant_workflow.config;

import com.school.multi_tenant_workflow.repository.TenantRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * This class ensures that all tenants stored in the Master DB
 * are loaded into the DataSource map when the application starts.
 */
@Component
public class TenantLoader implements CommandLineRunner {

    private final TenantRepository repository;
    private final TenantDataSourceManager manager;

    public TenantLoader(TenantRepository repository, TenantDataSourceManager manager) {
        this.repository = repository;
        this.manager = manager;
    }

    @Override
    public void run(String... args) {
        System.out.println(">>> Starting Tenant Loader: Initializing dynamic connections...");

        // Fetch all configurations from the Master DB
        repository.findAll().forEach(tenantConfig -> {
            try {
                // Register each tenant in the routing map
                manager.addTenant(tenantConfig);
                System.out.println("Successfully loaded connection for: " + tenantConfig.getName());
            } catch (Exception e) {
                System.err.println("Failed to load connection for tenant: " + tenantConfig.getName());
                e.printStackTrace();
            }
        });

        System.out.println(">>> Tenant Loader completed. System is ready.");
    }
}