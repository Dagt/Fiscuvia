/** Firm root metadata; all ordinary operations require fresh identity authorization. */
@org.springframework.modulith.ApplicationModule(allowedDependencies = {"platform","identity","audit"})
package com.fiscuvia.tenants;
