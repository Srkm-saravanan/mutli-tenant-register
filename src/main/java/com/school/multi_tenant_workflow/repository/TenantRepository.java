package com.school.multi_tenant_workflow.repository;

import com.school.multi_tenant_workflow.model.TenantConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TenantRepository extends JpaRepository<TenantConfig, Long> {
    // Spring Data JPA creates the save() and findAll() methods for us!
}