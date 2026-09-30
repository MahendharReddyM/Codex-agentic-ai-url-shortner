package dev.assessment.urlshortener.link;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.IntFunction;

import org.junit.jupiter.api.Test;

class InMemoryShortLinkRepositoryConcurrencyTest {

    @Test
    void admitsOnlyOneConcurrentWriterForTheSameCode() throws Exception {
        InMemoryShortLinkRepository repository = new InMemoryShortLinkRepository();

        List<Boolean> results = createConcurrently(64, index -> repository.createIfCodeAvailable(
                link("shared-code", "request-" + index)));

        assertThat(results).containsExactlyInAnyOrderElementsOf(expectedSingleWinner(64));
        assertThat(repository.findByCode("shared-code")).isPresent();
    }

    @Test
    void admitsOnlyOneConcurrentWriterForTheSameIdempotencyKey() throws Exception {
        InMemoryShortLinkRepository repository = new InMemoryShortLinkRepository();

        List<Boolean> results = createConcurrently(64, index -> repository.createIfCodeAvailable(
                link("code-" + index, "shared-request")));

        assertThat(results).containsExactlyInAnyOrderElementsOf(expectedSingleWinner(64));
        assertThat(repository.findByIdempotencyKey("shared-request")).isPresent();
    }

    @Test
    void createsIndependentLinksConcurrently() throws Exception {
        InMemoryShortLinkRepository repository = new InMemoryShortLinkRepository();

        List<Boolean> results = createConcurrently(128, index -> repository.createIfCodeAvailable(
                link("code-" + index, "request-" + index)));

        assertThat(results).containsOnly(true);
    }

    private List<Boolean> createConcurrently(int count, IntFunction<Boolean> operation) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(16)) {
            List<Future<Boolean>> futures = new ArrayList<>();
            for (int index = 0; index < count; index++) {
                int taskIndex = index;
                futures.add(executor.submit(() -> {
                    start.await();
                    return operation.apply(taskIndex);
                }));
            }
            start.countDown();
            List<Boolean> results = new ArrayList<>();
            for (Future<Boolean> future : futures) {
                results.add(future.get());
            }
            return results;
        }
    }

    private List<Boolean> expectedSingleWinner(int count) {
        List<Boolean> expected = new ArrayList<>();
        expected.add(true);
        for (int index = 1; index < count; index++) {
            expected.add(false);
        }
        return expected;
    }

    private ShortLink link(String code, String idempotencyKey) {
        return new ShortLink(
                code,
                URI.create("https://example.com/" + code),
                idempotencyKey,
                "fingerprint-" + code,
                Instant.parse("2026-01-01T00:00:00Z"),
                null);
    }
}
