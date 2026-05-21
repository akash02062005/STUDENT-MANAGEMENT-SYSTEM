package com.example.student;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

@Service
public class ActivityService {

    private final List<Activity> activities = new CopyOnWriteArrayList<>();
    private final List<AppNotification> notifications = new CopyOnWriteArrayList<>();
    private final List<Announcement> announcements = new CopyOnWriteArrayList<>();

    @Autowired
    private RealtimeService realtime;

    public Activity log(String type, String actor, String target, String message) {
        Activity a = new Activity(type, actor, target, message);
        activities.add(0, a);
        if (activities.size() > 200) activities.remove(activities.size() - 1);
        realtime.broadcast("activity", a);
        return a;
    }

    public AppNotification notify(String level, String title, String message, String forUser) {
        AppNotification n = new AppNotification(level, title, message, forUser);
        notifications.add(0, n);
        if (notifications.size() > 100) notifications.remove(notifications.size() - 1);
        realtime.broadcast("notification", n);
        return n;
    }

    // ----- Announcements -----
    public Announcement addAnnouncement(String author, String title, String body, String level) {
        Announcement a = new Announcement(author, title, body, level);
        announcements.add(0, a);
        if (announcements.size() > 100) announcements.remove(announcements.size() - 1);
        realtime.broadcast("announcement", a);
        // Also push as a notification to all users
        notify(a.getLevel(), a.getTitle(), a.getBody(), "*");
        log("ANNOUNCE", author, "broadcast", "Posted: " + title);
        return a;
    }

    public boolean removeAnnouncement(String id) {
        boolean removed = announcements.removeIf(a -> a.getId().equals(id));
        if (removed) realtime.broadcast("announcement-removed", id);
        return removed;
    }

    public Announcement pinAnnouncement(String id, boolean pinned) {
        for (Announcement a : announcements) {
            if (a.getId().equals(id)) {
                a.setPinned(pinned);
                realtime.broadcast("announcement", a);
                return a;
            }
        }
        return null;
    }

    public List<Announcement> getAnnouncements() {
        return announcements.stream()
                .sorted(Comparator
                        .comparing(Announcement::isPinned, Comparator.reverseOrder())
                        .thenComparingLong(Announcement::getTimestamp).reversed())
                .collect(Collectors.toList());
    }

    public List<Activity> getRecent(int limit) {
        return activities.stream().limit(limit).collect(Collectors.toList());
    }

    public List<AppNotification> getFor(String username) {
        return notifications.stream()
                .filter(n -> "*".equals(n.getForUser()) || n.getForUser().equalsIgnoreCase(username))
                .sorted(Comparator.comparingLong(AppNotification::getTimestamp).reversed())
                .collect(Collectors.toList());
    }

    public void markRead(String id) {
        notifications.stream().filter(n -> n.getId().equals(id)).forEach(n -> n.setRead(true));
    }

    public void markAllRead(String username) {
        notifications.stream()
                .filter(n -> "*".equals(n.getForUser()) || n.getForUser().equalsIgnoreCase(username))
                .forEach(n -> n.setRead(true));
    }

    public void clear() {
        activities.clear();
        notifications.clear();
        announcements.clear();
    }
}
