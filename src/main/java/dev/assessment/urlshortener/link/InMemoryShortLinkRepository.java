package dev.assessment.urlshortener.link;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;

import org.springframework.stereotype.Repository;

@Repository
public class InMemoryShortLinkRepository implements ShortLinkRepository {
    private static final int CREATION_LOCK_STRIPES = 64;

    private final ConcurrentMap<String, ShortLink> links = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String> idempotencyIndex = new ConcurrentHashMap<>();
    private final ReentrantLock[] creationLocks = new ReentrantLock[CREATION_LOCK_STRIPES];

    public InMemoryShortLinkRepository() {
        for (int index = 0; index < creationLocks.length; index++) {
            creationLocks[index] = new ReentrantLock();
        }
    }

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
    public boolean createIfCodeAvailable(ShortLink link) {
        int codeStripe = stripe("code:" + link.code());
        int idempotencyStripe = link.idempotencyKey() == null
                ? codeStripe
                : stripe("idempotency:" + link.idempotencyKey());
        int first = Math.min(codeStripe, idempotencyStripe);
        int second = Math.max(codeStripe, idempotencyStripe);

        creationLocks[first].lock();
        if (second != first) {
            creationLocks[second].lock();
        }
        try {
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
        } finally {
            if (second != first) {
                creationLocks[second].unlock();
            }
            creationLocks[first].unlock();
        }
    }

    @Override
    public void save(ShortLink link) {
        links.put(link.code(), link);
    }

    private int stripe(String key) {
        return Math.floorMod(key.hashCode(), creationLocks.length);
    }
}

