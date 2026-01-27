package com.school.multi_tenant_workflow.model;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
public class TenantConfig {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;           // Name of the school/client
    private String url;            // jdbc:mysql://localhost:3306/school_a
    private String username;
    private String password;
    private String driverClass;    // com.mysql.cj.jdbc.Driver or org.postgresql.Driver
}