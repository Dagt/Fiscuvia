package com.fiscuvia.platform;

import java.util.UUID;
import java.util.function.Supplier;

/** Internal application port. Call only after trusted server-side authorization (#27/#28).
 * A UUID is context, never evidence of membership. No HTTP adapter is provided by #26.
 */
public interface TenantTransactions {
    <T> T execute(UUID authorizedTenant, Supplier<T> work);
}
