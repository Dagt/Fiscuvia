package com.fiscuvia.audit;

import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;

/** Transport only. Instantiate with a separately authorized transport datasource/manager.
 * No bean, scheduler, user impersonation or business RLS context is installed by this issue. */
public interface OutboxProtocol {
    record Claim(UUID eventId,UUID tenantId,UUID leaseToken,Instant leasedUntil,int attempt) {
        @Override public String toString(){return "Claim[private]";}
    }
    enum Failure { RETRYABLE, UNSUPPORTED_VERSION, INVALID_PAYLOAD, PERMANENT }
    List<Claim> claim(int limit,int leaseMilliseconds);
    <T> T process(Claim claim,Supplier<T> idempotentLocalEffect);
    boolean retry(Claim claim,Failure failure);
}
