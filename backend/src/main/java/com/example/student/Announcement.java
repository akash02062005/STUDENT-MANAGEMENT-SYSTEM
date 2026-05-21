package com.example.student;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Announcement {
    private String id;
    private String author;
    private String title;
    private String body;
    private String level;   // info | success | warning | danger
    private long timestamp = Instant.now().toEpochMilli();
    private boolean pinned;

    public Announcement(String author, String title, String body, String level) {
        this.id = UUID.randomUUID().toString();
        this.author = author;
        this.title = title;
        this.body = body;
        this.level = level == null ? "info" : level;
        this.timestamp = Instant.now().toEpochMilli();
        this.pinned = false;
    }
}
