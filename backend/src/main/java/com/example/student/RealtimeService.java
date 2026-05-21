package com.example.student;

import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
public class RealtimeService {

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private final ScheduledExecutorService heartbeat = Executors.newSingleThreadScheduledExecutor();

    public RealtimeService() {
        // Keep connections alive with a heartbeat every 25 seconds
        heartbeat.scheduleAtFixedRate(() -> broadcast("ping", System.currentTimeMillis()), 25, 25, TimeUnit.SECONDS);
    }

    public SseEmitter register() {
        SseEmitter emitter = new SseEmitter(0L); // never timeout
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));
        emitters.add(emitter);
        try {
            emitter.send(SseEmitter.event().name("hello").data("connected"));
        } catch (IOException ignored) {}
        return emitter;
    }

    public void broadcast(String event, Object payload) {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name(event).data(payload));
            } catch (Exception e) {
                emitters.remove(emitter);
            }
        }
    }

    public int activeConnections() {
        return emitters.size();
    }
}
