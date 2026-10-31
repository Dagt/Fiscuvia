package com.fiscuvia.identity;

import java.util.UUID;
import java.util.function.Supplier;

/** Internal application port. The caller identity is always resolved from the authenticated session. */
public interface AuthorizedTenantOperations {
    <T> T execute(UUID tenant, boolean privileged, Supplier<T> work);
}
