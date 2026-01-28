package com.school.multi_tenant_workflow.controller;

import com.school.multi_tenant_workflow.model.AttendanceRecord;
import com.school.multi_tenant_workflow.model.Student;
import com.school.multi_tenant_workflow.model.TenantConfig;
import com.school.multi_tenant_workflow.repository.AttendanceRepository;
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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Controller
public class TenantController {

    private final TenantRepository tenantRepository;
    private final LiquibaseService liquibaseService;
    private final StudentRepository studentRepository;
    private final AttendanceRepository attendanceRepository;
    private final TenantDataSourceManager tenantDataSourceManager;

    public TenantController(TenantRepository tenantRepository,
                            LiquibaseService liquibaseService,
                            StudentRepository studentRepository,
                            AttendanceRepository attendanceRepository,
                            TenantDataSourceManager tenantDataSourceManager) {
        this.tenantRepository = tenantRepository;
        this.liquibaseService = liquibaseService;
        this.studentRepository = studentRepository;
        this.attendanceRepository = attendanceRepository;
        this.tenantDataSourceManager = tenantDataSourceManager;
    }

    @GetMapping("/")
    public String index(Model model) {
        // Force context clear to ensure we read the tenant list from Master DB
        TenantContext.clear();
        model.addAttribute("tenants", tenantRepository.findAll());
        model.addAttribute("tenantConfig", new TenantConfig());
        return "index";
    }

    @PostMapping("/provision")
    public String provision(@ModelAttribute TenantConfig config) {
        try {
            // Master DB must be active to save the new config
            TenantContext.clear();

            liquibaseService.runMigration(
                    config.getUrl(),
                    config.getUsername(),
                    config.getPassword(),
                    config.getDriverClass()
            );

            tenantRepository.save(config);
            tenantDataSourceManager.addTenant(config);

            return "redirect:/?success=Provisioned";
        } catch (Exception e) {
            String errorMsg = URLEncoder.encode(e.getMessage(), StandardCharsets.UTF_8);
            return "redirect:/?error=" + errorMsg;
        }
    }

    @PostMapping("/select-tenant")
    public String selectTenant(@RequestParam("tenantId") Long id, HttpSession session) {
        // CRITICAL: Clear context so we look for tenant_config in the Master MySQL DB
        TenantContext.clear();

        TenantConfig config = tenantRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Tenant not found"));

        tenantDataSourceManager.addTenant(config);

        // Auto-patch the school DB (MySQL or Postgres) with new tables like attendance
        try {
            liquibaseService.runMigration(
                    config.getUrl(),
                    config.getUsername(),
                    config.getPassword(),
                    config.getDriverClass()
            );
        } catch (Exception e) {
            System.err.println("Auto-patch failed: " + e.getMessage());
        }

        // Now set the context for the workspace
        TenantContext.setCurrentTenant(config.getName());
        session.setAttribute("CURRENT_TENANT_NAME", config.getName());

        return "redirect:/workspace";
    }

    @GetMapping("/workspace")
    public String workspace(Model model, HttpSession session) {
        String tenant = (String) session.getAttribute("CURRENT_TENANT_NAME");
        if (tenant == null) return "redirect:/";

        try {
            TenantContext.setCurrentTenant(tenant);
            model.addAttribute("activeTenant", tenant);
            model.addAttribute("students", studentRepository.findAll());
            return "workspace";
        } finally {
            TenantContext.clear();
        }
    }

    @PostMapping("/add-student")
    public String addStudent(@ModelAttribute Student student, HttpSession session) {
        String tenant = (String) session.getAttribute("CURRENT_TENANT_NAME");
        if (tenant == null) return "redirect:/";

        try {
            TenantContext.setCurrentTenant(tenant);
            studentRepository.save(student);
            return "redirect:/workspace";
        } finally {
            TenantContext.clear();
        }
    }

    @GetMapping("/attendance")
    public String showAttendancePage(Model model, HttpSession session) {
        String tenant = (String) session.getAttribute("CURRENT_TENANT_NAME");
        if (tenant == null) return "redirect:/";

        try {
            TenantContext.setCurrentTenant(tenant);
            model.addAttribute("activeTenant", tenant);
            model.addAttribute("students", studentRepository.findAll());
            model.addAttribute("today", LocalDate.now());
            return "attendance";
        } finally {
            TenantContext.clear();
        }
    }

    @PostMapping("/submit-attendance")
    public String submitAttendance(@RequestParam Map<String, String> allParams, HttpSession session) {
        String tenant = (String) session.getAttribute("CURRENT_TENANT_NAME");
        if (tenant == null) return "redirect:/";

        try {
            TenantContext.setCurrentTenant(tenant);
            List<AttendanceRecord> records = new ArrayList<>();
            LocalDate today = LocalDate.now();

            allParams.forEach((key, value) -> {
                if (key.startsWith("status_")) {
                    Long studentId = Long.parseLong(key.replace("status_", ""));
                    AttendanceRecord record = new AttendanceRecord();
                    record.setStudentId(studentId);
                    record.setStatus(value);
                    record.setDate(today);
                    records.add(record);
                }
            });

            attendanceRepository.saveAll(records);
            return "redirect:/workspace?success=AttendanceSaved";
        } finally {
            TenantContext.clear();
        }
    }
}