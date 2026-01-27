package com.school.multi_tenant_workflow.repository;

import com.school.multi_tenant_workflow.model.Student;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StudentRepository extends JpaRepository<Student, Long> {
    // This will handle saving students to whichever DB is active!
}