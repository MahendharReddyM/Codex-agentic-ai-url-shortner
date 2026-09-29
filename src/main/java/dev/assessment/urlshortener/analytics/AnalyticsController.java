package dev.assessment.urlshortener.analytics;

import dev.assessment.urlshortener.link.ShortLinkService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/links/{code}/analytics")
public class AnalyticsController {
    private final AnalyticsService analyticsService;
    private final ShortLinkService shortLinkService;

    public AnalyticsController(AnalyticsService analyticsService, ShortLinkService shortLinkService) {
        this.analyticsService = analyticsService;
        this.shortLinkService = shortLinkService;
    }

    @GetMapping
    LinkAnalyticsResponse analytics(@PathVariable String code) {
        shortLinkService.get(code);
        return analyticsService.summarize(code);
    }
}

