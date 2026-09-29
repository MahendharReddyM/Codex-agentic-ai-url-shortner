package dev.assessment.urlshortener.link;

import java.util.Optional;

public interface ShortLinkRepository {
    Optional<ShortLink> findByCode(String code);

    Optional<ShortLink> findByIdempotencyKey(String idempotencyKey);

    boolean createIfCodeAvailable(ShortLink link);

    void save(ShortLink link);
}

