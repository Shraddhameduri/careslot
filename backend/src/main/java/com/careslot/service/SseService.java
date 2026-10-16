package com.careslot.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** Fan-out of slot-availability changes to SSE subscribers. */
@Service
public class SseService {

    private static final Logger log = LoggerFactory.getLogger(SseService.class);

    private record Channel(Long tenantId, Long providerId, LocalDate date) {}

    private final Map<Channel, List<SseEmitter>> subscribers = new ConcurrentHashMap<>();

    public SseEmitter subscribe(Long tenantId, Long providerId, LocalDate date) {
        SseEmitter emitter = new SseEmitter(30 * 60 * 1000L);
        Channel channel = new Channel(tenantId, providerId, date);
        subscribers.computeIfAbsent(channel, c -> new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onCompletion(() -> remove(channel, emitter));
        emitter.onTimeout(() -> remove(channel, emitter));
        emitter.onError(e -> remove(channel, emitter));
        try {
            emitter.send(SseEmitter.event().name("connected").data("{\"ok\":true}"));
        } catch (Exception e) {
            remove(channel, emitter);
        }
        return emitter;
    }

    public void publish(Long tenantId, Long providerId, LocalDate date, Long slotId, String status) {
        List<SseEmitter> emitters = subscribers.get(new Channel(tenantId, providerId, date));
        if (emitters == null || emitters.isEmpty()) return;
        String payload = "{\"slotId\":%d,\"status\":\"%s\"}".formatted(slotId, status);
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("slot-updated").data(payload));
            } catch (Exception e) {
                remove(new Channel(tenantId, providerId, date), emitter);
                log.debug("Dropped dead SSE subscriber");
            }
        }
    }

    private void remove(Channel channel, SseEmitter emitter) {
        List<SseEmitter> list = subscribers.get(channel);
        if (list != null) {
            list.remove(emitter);
            if (list.isEmpty()) subscribers.remove(channel);
        }
    }
}
