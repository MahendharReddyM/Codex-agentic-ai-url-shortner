package dev.assessment.urlshortener.link;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.stereotype.Repository;

@Repository
public class InMemoryShortLinkRepository implements ShortLinkRepository {
    private final ConcurrentMap<String, ShortLink> links = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String> idempotencyIndex = new ConcurrentHashMap<>();

    @Override
    public Optional<ShortLink> findByCode(String code) {
        return Optional.ofNullable(links.get(code));
    }

    @Override
    public Optional<ShortLink> findByIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(idempotencyIndex.get(idempotencyKey)).flatMap(this::findByCode);
    }

    @Override
    public synchronized boolean createIfCodeAvailable(ShortLink link) {
        if (links.containsKey(link.code())) {
            return false;
        }
        if (link.idempotencyKey() != null && idempotencyIndex.containsKey(link.idempotencyKey())) {
            return false;
        }
        links.put(link.code(), link);
        if (link.idempotencyKey() != null) {
            idempotencyIndex.put(link.idempotencyKey(), link.code());
        }
        return true;
    }

    @Override
    public void save(ShortLink link) {
        links.put(link.code(), link);
    }
}

