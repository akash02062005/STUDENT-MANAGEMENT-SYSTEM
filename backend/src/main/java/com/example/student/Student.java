package com.example.student;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "students")
public class Student {
    @Id
    private String id;
    private String studentId; // REG.NO
    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private String department;
    private String batch;       // e.g. 2023-27
    private int age;
    private String gender;
    private String address;
    private String avatarColor; // for UI

    // Semester GPAs (10-point scale)
    private double gpa1;
    private double gpa2;
    private double gpa3;
    private double gpa4;
    private double gpa5;

    // Cumulative CGPAs (10-point scale)
    private double cgpa1;
    private double cgpa2;
    private double cgpa3;
    private double cgpa4;
    private double cgpa5;

    // Core fields
    private int attendance;        // 0-100
    private double placementScore; // 0-100
    private boolean isAtRisk;
    private int backlogs;

    // Metadata
    private long createdAt = Instant.now().toEpochMilli();
    private long updatedAt = Instant.now().toEpochMilli();
}
