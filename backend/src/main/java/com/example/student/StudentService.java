package com.example.student;

import com.example.student.auth.User;
import com.example.student.auth.UserRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class StudentService {

    @Autowired(required = false)
    private StudentRepository repository;

    @Autowired(required = false)
    private UserRepository userRepository;

    @Autowired
    private RealtimeService realtime;

    @Autowired
    private ActivityService activityService;

    private final String CSV_PATH = "D:\\tmp\\cgpa_data.csv";

    private final Map<String, Student> fallbackStore = new ConcurrentHashMap<>();
    private volatile boolean mongoHealthy = true;

    private static final String[] FIRST_NAMES = {
            "Aarav", "Vivaan", "Aditya", "Diya", "Ananya", "Ishaan", "Kabir", "Saanvi",
            "Reyansh", "Myra", "Arjun", "Anika", "Vihaan", "Aadhya", "Krishna", "Pari",
            "Aryan", "Riya", "Dev", "Tara", "Rohan", "Meera", "Yash", "Sara",
            "Karan", "Kiara", "Neel", "Aanya", "Ved", "Navya"
    };

    private static final String[] LAST_NAMES = {
            "Sharma", "Verma", "Iyer", "Reddy", "Patel", "Singh", "Kumar", "Nair",
            "Gupta", "Mehta", "Joshi", "Khan", "Pillai", "Das", "Bose", "Menon",
            "Rao", "Shah", "Chopra", "Bhatt"
    };

    private static final String[] AVATAR_COLORS = {
            "#6366f1", "#ec4899", "#f59e0b", "#10b981", "#06b6d4",
            "#8b5cf6", "#ef4444", "#84cc16", "#3b82f6", "#f97316"
    };

    @PostConstruct
    public void init() {
        // Probe Mongo. If not reachable, seed the in-memory store with synthetic data.
        try {
            if (repository != null) {
                long count = repository.count();
                mongoHealthy = true;
                if (count == 0) {
                    seedFallback();
                    fallbackStore.values().forEach(this::saveToMongoQuiet);
                }
            } else {
                mongoHealthy = false;
                seedFallback();
            }
        } catch (Exception e) {
            mongoHealthy = false;
            seedFallback();
        }
    }

    private void saveToMongoQuiet(Student s) {
        try {
            if (repository != null) {
                repository.save(s);
                ensureUserAccount(s);
            }
        } catch (Exception e) {
            mongoHealthy = false;
        }
    }

    private void seedFallback() {
        if (!fallbackStore.isEmpty()) return;
        Random r = new Random(7);
        for (int i = 1; i <= 28; i++) {
            String regNo = String.format("CSE23%03d", i);
            Student s = new Student();
            s.setStudentId(regNo);
            s.setFirstName(FIRST_NAMES[r.nextInt(FIRST_NAMES.length)]);
            s.setLastName(LAST_NAMES[r.nextInt(LAST_NAMES.length)]);
            s.setDepartment("CSE");
            s.setBatch("2023-27");
            s.setEmail(regNo.toLowerCase() + "@univ.edu");
            s.setPhone("+91 9" + (100000000L + r.nextInt(900000000)));
            s.setAge(19 + r.nextInt(4));
            s.setGender(r.nextBoolean() ? "Male" : "Female");
            s.setAddress("Campus Block " + (char) ('A' + r.nextInt(6)) + ", Room " + (100 + r.nextInt(80)));
            s.setAvatarColor(AVATAR_COLORS[r.nextInt(AVATAR_COLORS.length)]);

            double base = 6.0 + r.nextDouble() * 3.8;
            double s1 = clamp(base + r.nextGaussian() * 0.3);
            double s2 = clamp(s1 + r.nextGaussian() * 0.4);
            double s3 = clamp(s2 + r.nextGaussian() * 0.4);
            double s4 = clamp(s3 + r.nextGaussian() * 0.35);
            double s5 = clamp(s4 + r.nextGaussian() * 0.35);
            s.setGpa1(round(s1)); s.setGpa2(round(s2)); s.setGpa3(round(s3)); s.setGpa4(round(s4)); s.setGpa5(round(s5));
            s.setCgpa1(round(s1));
            s.setCgpa2(round((s1 + s2) / 2.0));
            s.setCgpa3(round((s1 + s2 + s3) / 3.0));
            s.setCgpa4(round((s1 + s2 + s3 + s4) / 4.0));
            s.setCgpa5(round((s1 + s2 + s3 + s4 + s5) / 5.0));
            s.setAttendance(70 + r.nextInt(30));
            s.setBacklogs(s.getCgpa3() < 6.5 ? 1 + r.nextInt(2) : 0);
            s.setAtRisk(s.getCgpa3() < 7.0 || s.getBacklogs() > 0 || s.getAttendance() < 75);
            s.setPlacementScore(round(Math.min(100, s.getCgpa5() * 10 - s.getBacklogs() * 12 + (s.getAttendance() - 75) * 0.4)));

            fallbackStore.put(regNo, s);
        }
    }

    public void importDepartmentData() {
        File csv = new File(CSV_PATH);
        if (csv.exists()) {
            importFromCsv();
        } else {
            // No CSV? Reseed synthetic data so the system is always usable.
            if (isRepositoryActive()) {
                try { repository.deleteAll(); } catch (Exception ignored) {}
            }
            fallbackStore.clear();
            seedFallback();
            if (isRepositoryActive()) {
                fallbackStore.values().forEach(this::saveToMongoQuiet);
            }
        }
        activityService.log("IMPORT", "system", "students", "Department matrix synchronised (" + getAllStudents().size() + " records)");
        realtime.broadcast("students-changed", Map.of("count", getAllStudents().size()));
    }

    private void importFromCsv() {
        if (isRepositoryActive()) {
            try { repository.deleteAll(); } catch (Exception ignored) {}
        }
        fallbackStore.clear();

        try (BufferedReader br = new BufferedReader(new FileReader(CSV_PATH))) {
            String line;
            int lineNumber = 0;
            while ((line = br.readLine()) != null) {
                lineNumber++;
                if (lineNumber <= 4) continue;

                String[] v = line.split(",");
                if (v.length < 67) continue;

                Student s = new Student();
                String regNo = v[1].trim();
                s.setStudentId(regNo);

                String name = v[2].trim();
                String[] nameParts = name.split(" ");
                s.setFirstName(nameParts[0]);
                s.setLastName(nameParts.length > 1 ? nameParts[1] : "");
                s.setDepartment("CSE");
                s.setBatch("2023-27");
                s.setEmail(regNo + "@univ.edu");
                s.setAge(20);
                s.setAvatarColor(AVATAR_COLORS[Math.abs(regNo.hashCode()) % AVATAR_COLORS.length]);

                try {
                    double s1 = parseDoubleSafe(v[21]);
                    double s2 = parseDoubleSafe(v[43]);
                    double s3 = parseDoubleSafe(v[65]);
                    s.setGpa1(Math.min(10.0, s1));
                    s.setGpa2(Math.min(10.0, s2));
                    s.setGpa3(Math.min(10.0, s3));
                    double cum = parseDoubleSafe(v[67]);
                    s.setCgpa1(s1);
                    s.setCgpa2(round((s1 + s2) / 2.0));
                    s.setCgpa3(Math.min(10.0, cum));
                    double trend = ((s2 - s1) + (s3 - s2)) / 2.0;
                    double s4 = clamp(s3 + (trend * 0.7));
                    double s5 = clamp(s4 + (trend * 0.5));
                    s.setGpa4(round(s4));
                    s.setCgpa4(round(Math.min(10.0, (s.getCgpa3() * 3 + s4) / 4.0)));
                    s.setGpa5(round(s5));
                    s.setCgpa5(round(Math.min(10.0, (s.getCgpa4() * 4 + s5) / 5.0)));

                    s.setAttendance(75 + new Random().nextInt(25));
                    int b = 0;
                    if (s1 < 5.0) b++; if (s2 < 5.0) b++; if (s3 < 5.0) b++;
                    s.setBacklogs(b);
                    s.setAtRisk(s.getCgpa3() < 7.0 || b > 0);
                    s.setPlacementScore(round(Math.min(100.0, s.getCgpa5() * 10 - (b * 15))));
                } catch (Exception e) {
                    continue;
                }

                save(s, "system");
            }
        } catch (IOException e) {
            throw new RuntimeException("Local student dataset not found at " + CSV_PATH);
        }
    }

    public List<Student> getAllStudents() {
        if (isRepositoryActive()) {
            try { return repository.findAll(); }
            catch (Exception e) { mongoHealthy = false; }
        }
        return new ArrayList<>(fallbackStore.values());
    }

    public List<Student> search(String query, String status, String sortBy, String order) {
        String q = query == null ? "" : query.toLowerCase().trim();
        List<Student> list = getAllStudents().stream().filter(s -> {
            if (q.isEmpty()) return true;
            return (s.getFirstName() + " " + s.getLastName() + " " + s.getStudentId() + " " + s.getEmail())
                    .toLowerCase().contains(q);
        }).filter(s -> {
            if (status == null || status.isEmpty() || status.equalsIgnoreCase("all")) return true;
            return switch (status.toLowerCase()) {
                case "risk", "at-risk" -> s.isAtRisk();
                case "ready", "placement-ready" -> s.getCgpa5() >= 7.5 && s.getBacklogs() == 0;
                case "elite" -> s.getCgpa5() >= 9.0;
                case "stable" -> !s.isAtRisk() && s.getCgpa5() < 7.5;
                default -> true;
            };
        }).collect(Collectors.toList());

        Comparator<Student> cmp = switch (sortBy == null ? "name" : sortBy) {
            case "name", "firstName" -> Comparator
                    .comparing((Student s) -> ((s.getFirstName() == null ? "" : s.getFirstName()) + " "
                            + (s.getLastName() == null ? "" : s.getLastName())).trim().toLowerCase());
            case "cgpa", "cgpa5" -> Comparator.comparingDouble(Student::getCgpa5);
            case "attendance" -> Comparator.comparingInt(Student::getAttendance);
            case "placement", "placementScore" -> Comparator.comparingDouble(Student::getPlacementScore);
            case "id", "studentId" -> Comparator.comparing(Student::getStudentId);
            default -> Comparator
                    .comparing((Student s) -> ((s.getFirstName() == null ? "" : s.getFirstName()) + " "
                            + (s.getLastName() == null ? "" : s.getLastName())).trim().toLowerCase());
        };
        if ("desc".equalsIgnoreCase(order)) cmp = cmp.reversed();
        list.sort(cmp);
        return list;
    }

    public List<Student> getTopPerformers(int limit) {
        return getAllStudents().stream()
                .sorted(Comparator.comparingDouble(Student::getCgpa5).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    public Map<String, Object> getDepartmentAnalytics() {
        List<Student> all = getAllStudents();
        Map<String, Object> a = new LinkedHashMap<>();
        if (all.isEmpty()) {
            a.put("trajectoryAvg", List.of(0, 0, 0, 0, 0));
            a.put("placementEligible", 0);
            a.put("gradeSpread", Map.of("Elite (9.0+)", 0, "Advanced (8.0-9.0)", 0, "Stable (7.0-8.0)", 0, "At-Risk (< 7.0)", 0));
            a.put("totalStudents", 0);
            a.put("avgCgpa", 0);
            a.put("avgAttendance", 0);
            a.put("atRisk", 0);
            a.put("backlogs", 0);
            a.put("attendanceBuckets", Map.of("90+", 0, "75-89", 0, "<75", 0));
            return a;
        }

        a.put("trajectoryAvg", List.of(
                round(all.stream().mapToDouble(Student::getGpa1).average().orElse(0)),
                round(all.stream().mapToDouble(Student::getGpa2).average().orElse(0)),
                round(all.stream().mapToDouble(Student::getGpa3).average().orElse(0)),
                round(all.stream().mapToDouble(Student::getGpa4).average().orElse(0)),
                round(all.stream().mapToDouble(Student::getGpa5).average().orElse(0))));

        long eligible = all.stream().filter(s -> s.getCgpa5() >= 7.5 && s.getBacklogs() == 0).count();
        a.put("placementEligible", eligible);

        Map<String, Long> spread = new LinkedHashMap<>();
        spread.put("Elite (9.0+)", all.stream().filter(s -> s.getCgpa3() >= 9.0).count());
        spread.put("Advanced (8.0-9.0)", all.stream().filter(s -> s.getCgpa3() >= 8.0 && s.getCgpa3() < 9.0).count());
        spread.put("Stable (7.0-8.0)", all.stream().filter(s -> s.getCgpa3() >= 7.0 && s.getCgpa3() < 8.0).count());
        spread.put("At-Risk (< 7.0)", all.stream().filter(s -> s.getCgpa3() < 7.0).count());
        a.put("gradeSpread", spread);

        a.put("totalStudents", all.size());
        a.put("avgCgpa", round(all.stream().mapToDouble(Student::getCgpa5).average().orElse(0)));
        a.put("avgAttendance", (int) Math.round(all.stream().mapToInt(Student::getAttendance).average().orElse(0)));
        a.put("atRisk", all.stream().filter(Student::isAtRisk).count());
        a.put("backlogs", all.stream().mapToInt(Student::getBacklogs).sum());

        Map<String, Long> bk = new LinkedHashMap<>();
        bk.put("90+", all.stream().filter(s -> s.getAttendance() >= 90).count());
        bk.put("75-89", all.stream().filter(s -> s.getAttendance() >= 75 && s.getAttendance() < 90).count());
        bk.put("<75", all.stream().filter(s -> s.getAttendance() < 75).count());
        a.put("attendanceBuckets", bk);

        a.put("updatedAt", Instant.now().toEpochMilli());
        return a;
    }

    public Optional<Student> getStudentByStudentId(String studentId) {
        if (isRepositoryActive()) {
            try { return repository.findByStudentId(studentId); }
            catch (Exception e) { mongoHealthy = false; }
        }
        return Optional.ofNullable(fallbackStore.get(studentId));
    }

    public Student save(Student student, String actor) {
        if (student.getStudentId() == null || student.getStudentId().isEmpty()) return null;
        recompute(student);
        student.setUpdatedAt(Instant.now().toEpochMilli());
        if (student.getAvatarColor() == null) {
            student.setAvatarColor(AVATAR_COLORS[Math.abs(student.getStudentId().hashCode()) % AVATAR_COLORS.length]);
        }
        boolean isNew = !fallbackStore.containsKey(student.getStudentId());
        fallbackStore.put(student.getStudentId(), student);
        if (isRepositoryActive()) {
            try {
                repository.save(student);
                ensureUserAccount(student);
            } catch (Exception e) { mongoHealthy = false; }
        }
        activityService.log(isNew ? "CREATE" : "UPDATE", actor == null ? "system" : actor,
                student.getStudentId(),
                (isNew ? "Enrolled " : "Updated ") + student.getFirstName() + " " + student.getLastName());
        realtime.broadcast("students-changed", Map.of("studentId", student.getStudentId(), "type", isNew ? "create" : "update"));
        return student;
    }

    private void ensureUserAccount(Student s) {
        if (userRepository == null) return;
        try {
            if (userRepository.findByUsername(s.getStudentId()).isEmpty()) {
                String prefix = s.getFirstName() != null && !s.getFirstName().isEmpty()
                        ? s.getFirstName().substring(0, Math.min(2, s.getFirstName().length())).toUpperCase() : "ST";
                String regNo = s.getStudentId();
                String suffix = regNo.length() >= 3 ? regNo.substring(regNo.length() - 3) : "000";
                User u = new User();
                u.setUsername(regNo);
                u.setPassword(prefix + suffix);
                u.setRole("STUDENT");
                u.setStudentId(regNo);
                userRepository.save(u);
            }
        } catch (Exception ignored) {}
    }

    public void deleteStudent(String studentId, String actor) {
        Student s = fallbackStore.remove(studentId);
        if (isRepositoryActive()) {
            try { repository.findByStudentId(studentId).ifPresent(repository::delete); }
            catch (Exception e) { mongoHealthy = false; }
        }
        String name = s == null ? studentId : (s.getFirstName() + " " + s.getLastName());
        activityService.log("DELETE", actor == null ? "system" : actor, studentId, "Removed " + name + " from matrix");
        realtime.broadcast("students-changed", Map.of("studentId", studentId, "type", "delete"));
    }

    public Optional<Student> updateAttendance(String studentId, int attendance, String actor) {
        return getStudentByStudentId(studentId).map(s -> {
            s.setAttendance(Math.max(0, Math.min(100, attendance)));
            recompute(s);
            return save(s, actor);
        });
    }

    public Optional<Student> updateGrade(String studentId, int semester, double gpa, String actor) {
        if (semester < 1 || semester > 5) return Optional.empty();
        return getStudentByStudentId(studentId).map(s -> {
            try {
                Student.class.getMethod("setGpa" + semester, double.class).invoke(s, clamp(gpa));
            } catch (Exception ignored) {}
            recompute(s);
            return save(s, actor);
        });
    }

    private void recompute(Student s) {
        double[] g = { s.getGpa1(), s.getGpa2(), s.getGpa3(), s.getGpa4(), s.getGpa5() };
        double sum = 0; int n = 0;
        for (int i = 0; i < g.length; i++) {
            sum += g[i]; n++;
            double avg = round(sum / n);
            try { Student.class.getMethod("setCgpa" + (i + 1), double.class).invoke(s, avg); } catch (Exception ignored) {}
        }
        s.setAtRisk(s.getCgpa3() < 7.0 || s.getBacklogs() > 0 || s.getAttendance() < 75);
        s.setPlacementScore(round(Math.min(100, s.getCgpa5() * 10 - s.getBacklogs() * 12 + (s.getAttendance() - 75) * 0.4)));
    }

    public Map<String, Object> health() {
        return Map.of(
                "mongo", mongoHealthy,
                "students", getAllStudents().size(),
                "uptime", System.currentTimeMillis()
        );
    }

    private boolean isRepositoryActive() {
        return repository != null && mongoHealthy;
    }

    private double parseDoubleSafe(String val) {
        if (val == null || val.trim().isEmpty() || val.contains("U")) return 0.0;
        try { return Double.parseDouble(val.trim()); }
        catch (NumberFormatException e) { return 0.0; }
    }

    private double round(double v) { return Math.round(v * 100.0) / 100.0; }
    private double clamp(double v) { return Math.max(0.0, Math.min(10.0, v)); }
}
