package com.school.multi_tenant_workflow.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;
@Entity
@Table(name = "attendance_records")
@Data
public class AttendanceRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long studentId;
    private String status; // Present, Absent, OD
    private LocalDate date;
}
