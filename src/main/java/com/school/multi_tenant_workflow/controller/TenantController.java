package com.school.multi_tenant_workflow.controller;

import com.school.multi_tenant_workflow.model.Student;
import com.school.multi_tenant_workflow.model.TenantConfig;
import com.school.multi_tenant_workflow.repository.StudentRepository;
import com.school.multi_tenant_workflow.repository.TenantRepository;
import com.school.multi_tenant_workflow.service.LiquibaseService;
import com.school.multi_tenant_workflow.config.TenantContext;
import com.school.multi_tenant_workflow.config.TenantDataSourceManager;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Controller
public class TenantController {

    private final TenantRepository tenantRepository;
    private final LiquibaseService liquibaseService;
    private final StudentRepository studentRepository;
    private final TenantDataSourceManager tenantDataSourceManager;

    public TenantController(TenantRepository tenantRepository,
                            LiquibaseService liquibaseService,
                            StudentRepository studentRepository,
                            TenantDataSourceManager tenantDataSourceManager) {
        this.tenantRepository = tenantRepository;
        this.liquibaseService = liquibaseService;
        this.studentRepository = studentRepository;
        this.tenantDataSourceManager = tenantDataSourceManager;
    }

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("tenants", tenantRepository.findAll());
        model.addAttribute("tenantConfig", new TenantConfig());
        return "index";
    }

    @PostMapping("/provision")
    public String provision(@ModelAttribute TenantConfig config) {
        try {
            // 1. Create DB and run Liquibase (Automated as per diagram)
            liquibaseService.runMigration(
                    config.getUrl(),
                    config.getUsername(),
                    config.getPassword(),
                    config.getDriverClass()
            );

            // 2. Save metadata to Master DB (The "Property File" replacement)
            tenantRepository.save(config);

            // 3. Load connection into Switchboard immediately
            tenantDataSourceManager.addTenant(config);

            return "redirect:/?success=Provisioned";
        } catch (Exception e) {
            String errorMsg = URLEncoder.encode(e.getMessage(), StandardCharsets.UTF_8);
            return "redirect:/?error=" + errorMsg;
        }
    }

    @PostMapping("/select-tenant")
    public String selectTenant(@RequestParam("tenantId") Long id, HttpSession session) {
        TenantConfig config = tenantRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Tenant not found"));

        String tenantName = config.getName();

        // Ensure the connection is in the Manager's Map before switching
        tenantDataSourceManager.addTenant(config);

        TenantContext.setCurrentTenant(tenantName);
        session.setAttribute("CURRENT_TENANT_NAME", tenantName);

        return "redirect:/workspace";
    }

    @GetMapping("/workspace")
    public String workspace(Model model, HttpSession session) {
        String tenant = (String) session.getAttribute("CURRENT_TENANT_NAME");
        if (tenant == null) {
            return "redirect:/";
        }

        try {
            // Set context so repository knows which DB to query
            TenantContext.setCurrentTenant(tenant);

            model.addAttribute("activeTenant", tenant);
            model.addAttribute("students", studentRepository.findAll());
            return "workspace";
        } catch (Exception e) {
            return "redirect:/?error=CouldNotAccessWorkspace";
        } finally {
            // Crucial: Clear context so we don't bleed into other user threads
            TenantContext.clear();
        }
    }

    @PostMapping("/add-student")
    public String addStudent(@ModelAttribute Student student, HttpSession session) {
        String tenant = (String) session.getAttribute("CURRENT_TENANT_NAME");
        if (tenant == null) {
            return "redirect:/";
        }

        try {
            TenantContext.setCurrentTenant(tenant);
            studentRepository.save(student);
            return "redirect:/workspace";
        } finally {
            TenantContext.clear();
        }
    }
}