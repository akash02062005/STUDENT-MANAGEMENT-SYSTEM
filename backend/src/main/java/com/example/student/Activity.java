package com.example.student;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Activity {
    private String id;
    private String type;        // CREATE | UPDATE | DELETE | LOGIN | IMPORT | NOTIFY
    private String actor;       // username
    private String target;      // studentId or resource
    private String message;
    private long timestamp = Instant.now().toEpochMilli();

    public Activity(String type, String actor, String target, String message) {
        this.id = java.util.UUID.randomUUID().toString();
        this.type = type;
        this.actor = actor;
        this.target = target;
        this.message = message;
        this.timestamp = Instant.now().toEpochMilli();
    }
}
