package dev.assessment.urlshortener.analytics;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import dev.assessment.urlshortener.config.ReliabilityProperties;
import org.springframework.stereotype.Repository;

@Repository
public class InMemoryAnalyticsRepository implements AnalyticsRepository {
    private final ConcurrentMap<String, Deque<ClickEvent>> events = new ConcurrentHashMap<>();
    private final int maximumEventsPerLink;

    public InMemoryAnalyticsRepository(ReliabilityProperties properties) {
        this.maximumEventsPerLink = properties.maxAnalyticsEventsPerLink();
    }

    @Override
    public void append(ClickEvent event) {
        Deque<ClickEvent> perLink = events.computeIfAbsent(event.code(), ignored -> new ArrayDeque<>());
        synchronized (perLink) {
            perLink.addLast(event);
            while (perLink.size() > maximumEventsPerLink) {
                perLink.removeFirst();
            }
        }
    }

    @Override
    public List<ClickEvent> findByCode(String code) {
        Deque<ClickEvent> perLink = events.get(code);
        if (perLink == null) {
            return List.of();
        }
        synchronized (perLink) {
            return new ArrayList<>(perLink);
        }
    }
}

