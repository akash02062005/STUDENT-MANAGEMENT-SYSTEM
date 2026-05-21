package com.example.student;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AppNotification {
    private String id;
    private String level;   // info | success | warning | danger
    private String title;
    private String message;
    private String forUser; // username, or "*" for all
    private boolean read;
    private long timestamp = Instant.now().toEpochMilli();

    public AppNotification(String level, String title, String message, String forUser) {
        this.id = java.util.UUID.randomUUID().toString();
        this.level = level;
        this.title = title;
        this.message = message;
        this.forUser = forUser;
        this.read = false;
        this.timestamp = Instant.now().toEpochMilli();
    }
}
