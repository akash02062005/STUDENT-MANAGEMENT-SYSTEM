package com.example.student.auth;

import com.example.student.ActivityService;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuthService {

    // required = false so the app still starts if MongoDB is offline / not on the classpath
    @Autowired(required = false)
    private UserRepository repository;

    @Autowired
    private ActivityService activityService;

    // In-memory store used as a fallback (and as the source of truth when Mongo is down).
    private final Map<String, User> fallback = new ConcurrentHashMap<>();
    private volatile boolean mongoHealthy = true;

    @PostConstruct
    public void init() {
        seed("admin", "admin123", "ADMIN", null);
        seed("faculty", "faculty123", "ADMIN", null);
        seed("demo", "demo123", "STUDENT", "CSE23001");
    }

    private void seed(String username, String password, String role, String studentId) {
        try {
            if (repository != null && repository.findByUsername(username).isEmpty()) {
                User u = new User();
                u.setUsername(username); u.setPassword(password); u.setRole(role); u.setStudentId(studentId);
                repository.save(u);
            }
        } catch (Exception e) {
            mongoHealthy = false;
        }
        if (!fallback.containsKey(username)) {
            User u = new User();
            u.setUsername(username); u.setPassword(password); u.setRole(role); u.setStudentId(studentId);
            fallback.put(username, u);
        }
    }

    public Optional<User> login(String username, String password) {
        if (username == null || password == null) return Optional.empty();
        Optional<User> result = Optional.empty();
        try {
            if (repository != null && mongoHealthy) {
                result = repository.findByUsername(username).filter(u -> password.equals(u.getPassword()));
            }
        } catch (Exception e) {
            mongoHealthy = false;
        }
        if (result.isEmpty()) {
            User u = fallback.get(username);
            if (u != null && password.equals(u.getPassword())) result = Optional.of(u);
        }
        result.ifPresent(u -> activityService.log("LOGIN", u.getUsername(), u.getUsername(), u.getRole() + " signed in"));
        return result;
    }

    public User register(User user) {
        if (user == null || user.getUsername() == null) {
            throw new RuntimeException("Username required");
        }
        if (fallback.containsKey(user.getUsername())) {
            throw new RuntimeException("Username already exists");
        }
        try {
            if (repository != null && mongoHealthy) {
                if (repository.findByUsername(user.getUsername()).isPresent()) {
                    throw new RuntimeException("Username already exists");
                }
                repository.save(user);
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception ignored) {
            mongoHealthy = false;
        }
        fallback.put(user.getUsername(), user);
        activityService.log("CREATE", "system", user.getUsername(), "Registered " + user.getRole());
        return user;
    }
}
