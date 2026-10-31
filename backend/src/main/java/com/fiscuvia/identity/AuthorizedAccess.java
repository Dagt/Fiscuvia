package com.fiscuvia.identity;

import java.util.UUID;

/** Attribution inside the callback only, never a reusable authorization credential or HTTP DTO. */
public interface AuthorizedAccess {
    record Actor(UUID userId) {}
    record Tenant(UUID tenantId) {}
    Actor actor();
    Tenant tenant();
    Capability capability();
    ResourceTarget target();
    /** Reject retained scope use after the callback or in a different transaction. */
    void requireCurrent();
}
