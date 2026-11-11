package com.fiscuvia.entitlements;

import com.fiscuvia.identity.AuthorizedAccess;
import java.util.UUID;

/** Call inside an AuthorizedTenantOperations callback; never accepts browser authority.
 * Reservations hold one future ACTIVE membership, identified by its real global user.
 * Confirmation occurs only with the resource mutation, not by incrementing a fictitious counter.
 * Future modules must supply a real measurement adapter and atomic mutation hook first.
 */
public interface Capacity {
    enum Result { RESERVED, CONFIRMED, RELEASED, UNCONFIGURED, EXPIRED, EXHAUSTED, ALREADY_ACTIVE, NOT_INTEGRATED }
    Result reserve(AuthorizedAccess access, Resource resource, UUID command, UUID reference, long units);
    Result release(AuthorizedAccess access, UUID reservation, UUID command);
}
