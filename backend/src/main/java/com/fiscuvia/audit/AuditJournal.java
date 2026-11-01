package com.fiscuvia.audit;

import java.util.UUID;

/** Requires the caller's existing authenticated/authorized JDBC transaction. Never starts a success transaction. */
public interface AuditJournal {
    enum EventCode { OWNER_BOOTSTRAPPED, INVITATION_ISSUED, INVITATION_REVOKED, INVITATION_ACCEPTED,
        MEMBERSHIP_SUSPENDED, MEMBERSHIP_REVOKED, AUTHORIZED_OPERATION_RECORDED, PRIVILEGED_OPERATION_DENIED }
    record Receipt(UUID eventId,EventCode eventCode,int schemaVersion,boolean replay) {}
    enum PrivilegedOperation { TENANT_SETTINGS_MANAGE, MEMBERSHIP_INVITE, MEMBERSHIP_ASSIGN_ROLE, MEMBERSHIP_DISABLE, AUDIT_VIEW }
    enum DenialReason { MEMBERSHIP_INACTIVE, CAPABILITY_UNCONFIGURED, RESOURCE_UNASSIGNED, MFA_REQUIRED, CONTEXT_REJECTED }
    record Denial(PrivilegedOperation operation,DenialReason reason,UUID commandId,UUID correlationId) {
        public Denial { java.util.Objects.requireNonNull(operation);java.util.Objects.requireNonNull(reason);java.util.Objects.requireNonNull(commandId);java.util.Objects.requireNonNull(correlationId); }
    }
    /** Bind a command before the existing identity command; SQL triggers record the actual fact. */
    void bindCommand(UUID commandId);
    /** Minimal idempotent infrastructure command. Replays return a receipt, never repeat work or cache arbitrary DTOs. */
    Receipt execute(UUID commandId,Runnable work);
    /** Only after rejection/rollback, with identity freshly verified/bound by identity in a new transaction. */
    void recordDenial(Denial denial);
}
