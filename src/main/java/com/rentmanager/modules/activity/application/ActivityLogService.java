package com.rentmanager.modules.activity.application;

import com.rentmanager.modules.activity.domain.model.ActivityLog;
import com.rentmanager.modules.activity.infrastructure.persistence.ActivityLogRepository;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class ActivityLogService {

    private static final Logger log = LoggerFactory.getLogger(ActivityLogService.class);

    // Sent every 20s to every open connection so intermediate proxies/dev servers
    // never see the connection go idle, and so the frontend's stability window
    // (STABLE_CONNECTION_MS) always has something to measure against even when
    // no real activity has happened.
    private static final long HEARTBEAT_INTERVAL_SECONDS = 20;

    private final ActivityLogRepository repository;

    // Keyed by tenant so a dashboard only ever receives its own tenant's activity.
    private final Map<UUID, List<SseEmitter>> emittersByTenant = new ConcurrentHashMap<>();

    private final ScheduledExecutorService heartbeatExecutor =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "activity-sse-heartbeat");
                t.setDaemon(true);
                return t;
            });

    {
        heartbeatExecutor.scheduleAtFixedRate(
                this::broadcastHeartbeat,
                HEARTBEAT_INTERVAL_SECONDS,
                HEARTBEAT_INTERVAL_SECONDS,
                TimeUnit.SECONDS
        );
    }

    public void record(
            UUID tenantId,
            String eventType,
            String entityType,
            UUID entityId,
            String entityName,
            UUID actorId,
            String actorName,
            Map<String, Object> metadata
    ) {
        ActivityLog activity = ActivityLog.record(
                tenantId, eventType, entityType, entityId, entityName, actorId, actorName, metadata
        );

        repository.save(activity);

        broadcast(tenantId, activity);
    }

    public List<ActivityLog> recent(UUID tenantId, int limit) {
        return repository.findByTenantIdOrderByCreatedAtDesc(tenantId, PageRequest.of(0, limit));
    }

    public SseEmitter subscribe(UUID tenantId) {
        SseEmitter emitter = new SseEmitter(0L); // no timeout; client's EventSource reconnects if dropped
        List<SseEmitter> emitters = emittersByTenant.computeIfAbsent(tenantId, id -> new CopyOnWriteArrayList<>());
        emitters.add(emitter);

        emitter.onCompletion(() -> removeEmitter(tenantId, emitter));
        emitter.onTimeout(() -> removeEmitter(tenantId, emitter));
        emitter.onError(e -> removeEmitter(tenantId, emitter));

        // Critical: without an initial write, the servlet container never flushes
        // response headers to the client, so the request sits at 0 bytes / pending
        // indefinitely until (if ever) an unrelated activity event happens to fire
        // for this tenant while the connection is still open. Sending an event
        // immediately on subscribe is what makes the connection observably "open".
        try {
            emitter.send(SseEmitter.event().name("connected").comment("stream open"));
        } catch (IOException e) {
            // Client disconnected before we even got the first write out; let the
            // container's onError/onCompletion callbacks handle cleanup, nothing
            // further to do here.
            removeEmitter(tenantId, emitter);
        }

        return emitter;
    }

    private void broadcastHeartbeat() {
        for (Map.Entry<UUID, List<SseEmitter>> entry : emittersByTenant.entrySet()) {
            UUID tenantId = entry.getKey();
            for (SseEmitter emitter : entry.getValue()) {
                try {
                    emitter.send(SseEmitter.event().comment("heartbeat"));
                } catch (IOException e) {
                    removeEmitter(tenantId, emitter);
                } catch (Exception e) {
                    log.warn("Failed to send heartbeat to a subscriber for tenant {}", tenantId, e);
                    removeEmitter(tenantId, emitter);
                }
            }
        }
    }

    private void broadcast(UUID tenantId, ActivityLog activity) {
        List<SseEmitter> emitters = emittersByTenant.get(tenantId);
        if (emitters == null || emitters.isEmpty()) return;

        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("activity").data(activity));
            } catch (IOException e) {
                removeEmitter(tenantId, emitter);
            } catch (Exception e) {
                log.warn("Failed to push activity to a subscriber", e);
                removeEmitter(tenantId, emitter);
            }
        }
    }

    private void removeEmitter(UUID tenantId, SseEmitter emitter) {
        List<SseEmitter> emitters = emittersByTenant.get(tenantId);
        if (emitters != null) {
            emitters.remove(emitter);
        }
    }

    @PreDestroy
    public void shutdown() {
        heartbeatExecutor.shutdownNow();
    }
}