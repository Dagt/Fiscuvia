package com.fiscuvia.platform;

/** Allowlisted startup diagnostic across modules; no input, secret or underlying throwable is accepted. */
public final class StartupGuardFailure extends IllegalStateException {
    public enum Kind { CONFIGURATION_REJECTED, DATABASE_PRIVILEGES }
    private final Kind kind;
    public StartupGuardFailure(Kind kind) { super(java.util.Objects.requireNonNull(kind).name()); this.kind=kind; }
    public Kind kind() { return kind; }
}
