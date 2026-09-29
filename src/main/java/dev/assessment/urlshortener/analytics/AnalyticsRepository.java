package dev.assessment.urlshortener.analytics;

import java.util.List;

public interface AnalyticsRepository {
    void append(ClickEvent event);

    List<ClickEvent> findByCode(String code);
}

