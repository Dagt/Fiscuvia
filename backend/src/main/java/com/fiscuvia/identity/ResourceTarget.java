package com.fiscuvia.identity;

import java.util.Objects;
import java.util.UUID;

/** Candidate UUIDs are not authorization. Collections require the resource RLS fence on every row. */
public record ResourceTarget(Kind kind, UUID id) {
    public enum Kind { TENANT, TAXPAYER, DOCUMENT, REPORT, AUDIT }
    public ResourceTarget { Objects.requireNonNull(kind); if (kind==Kind.TENANT && id!=null) throw new IllegalArgumentException("Invalid target"); }
    public static ResourceTarget tenant() { return new ResourceTarget(Kind.TENANT,null); }
    public static ResourceTarget item(Kind kind,UUID id) { return new ResourceTarget(kind,Objects.requireNonNull(id)); }
    public static ResourceTarget collection(Kind kind) { return new ResourceTarget(kind,null); }
}
