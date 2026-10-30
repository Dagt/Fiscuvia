package com.fiscuvia.platform.internal.tenancy;

/** Safe operational diagnostic, with no rejected identifier, SQL or driver exception attached. */
public final class TenantContextRejected extends IllegalStateException {
    TenantContextRejected() { super("Tenant transaction context rejected"); }
}
