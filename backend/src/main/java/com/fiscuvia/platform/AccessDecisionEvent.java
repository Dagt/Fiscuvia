package com.fiscuvia.platform;

import java.util.Objects;

/** Closed diagnostic allowlist. No IDs, SQL, user input, resource state or arbitrary maps. */
public record AccessDecisionEvent(Decision decision, Reason reason, Category category, Operation operation) {
    public enum Decision { PERMIT, DENY }
    public enum Reason { AUTHORIZED, AUTHENTICATION_REQUIRED, MEMBERSHIP_INACTIVE, CAPABILITY_UNCONFIGURED,
        RESOURCE_UNASSIGNED, MFA_REQUIRED, CONTEXT_REJECTED, INTERNAL_FAILURE }
    public enum Category { TENANT, TAXPAYER, DOCUMENT, REPORT, AUDIT }
    public enum Operation { READ, MANAGE, INVITE, ASSIGN, CREATE, EDIT, EXPORT, DOWNLOAD }
    public AccessDecisionEvent { Objects.requireNonNull(decision); Objects.requireNonNull(reason); Objects.requireNonNull(category); Objects.requireNonNull(operation); }
}
