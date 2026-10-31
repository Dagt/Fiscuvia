package com.fiscuvia.identity;

import java.util.UUID;
import java.util.function.Function;

/** Fresh session, membership, capability and resource checks in the same transaction as work.
 * Never accept an actor, role or a precomputed permit from an HTTP caller. */
public interface AuthorizedTenantOperations {
    <T> T execute(UUID tenant, Capability capability, ResourceTarget target, Function<AuthorizedAccess,T> work);
}
