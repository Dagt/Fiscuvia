package com.fiscuvia.platform.internal.observability;

import java.util.ArrayList;
import java.util.Set;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.config.MeterFilterReply;

final class PrivateMeterPolicy implements MeterFilter {
    private static final Set<String> NAMES = Set.of("http.server.requests", "http.server.requests.active",
            "jvm.memory.used", "jvm.memory.committed", "jvm.memory.max", "jvm.buffer.memory.used", "jvm.buffer.count",
            "jvm.buffer.total.capacity", "jvm.gc.pause", "jvm.gc.memory.allocated", "jvm.gc.memory.promoted",
            "jvm.gc.live.data.size", "jvm.gc.max.data.size", "jvm.threads.live", "jvm.threads.daemon", "jvm.threads.peak",
            "jvm.threads.states", "jvm.classes.loaded", "jvm.classes.unloaded", "process.uptime", "process.start.time",
            "process.cpu.usage", "system.cpu.usage", "system.cpu.count", "application.ready.time", "application.started.time",
            "jdbc.connections.active", "jdbc.connections.idle", "jdbc.connections.max", "jdbc.connections.min",
            "hikaricp.connections", "hikaricp.connections.active", "hikaricp.connections.idle", "hikaricp.connections.pending",
            "hikaricp.connections.max", "hikaricp.connections.min", "hikaricp.connections.acquire", "hikaricp.connections.usage",
            "hikaricp.connections.creation", "hikaricp.connections.timeout", "fiscuvia.database.available", "fiscuvia.migrations.ready");
    private static final Set<String> IDS = Set.of("G1 Eden Space", "G1 Survivor Space", "G1 Old Gen", "Metaspace",
            "Compressed Class Space", "CodeHeap 'non-nmethods'", "CodeHeap 'profiled nmethods'", "CodeHeap 'non-profiled nmethods'",
            "direct", "mapped", "mapped - 'non-volatile memory'");
    @Override public MeterFilterReply accept(Meter.Id id) { return NAMES.contains(id.getName()) ? MeterFilterReply.NEUTRAL : MeterFilterReply.DENY; }
    @Override public Meter.Id map(Meter.Id id) {
        var safe = new ArrayList<Tag>();
        for (Tag tag : id.getTags()) {
            String value = switch (tag.getKey()) {
                case "method" -> SafeRoutes.method(tag.getValue());
                case "uri" -> SafeRoutes.route(tag.getValue());
                case "status" -> tag.getValue().matches("[1-5][0-9]{2}") ? tag.getValue() : "500";
                case "outcome" -> Set.of("SUCCESS", "CLIENT_ERROR", "SERVER_ERROR").contains(tag.getValue()) ? tag.getValue() : "SERVER_ERROR";
                case "exception" -> "none".equals(tag.getValue()) ? "none" : "ERROR";
                case "area" -> Set.of("heap", "nonheap").contains(tag.getValue()) ? tag.getValue() : "OTHER";
                case "id" -> IDS.contains(tag.getValue()) ? tag.getValue() : "OTHER";
                case "pool" -> "fiscuvia-sessions".equals(tag.getValue()) ? "fiscuvia-sessions" : "fiscuvia-runtime";
                case "name" -> "dataSource";
                case "state" -> Set.of("new", "runnable", "blocked", "waiting", "timed-waiting", "terminated").contains(tag.getValue()) ? tag.getValue() : "OTHER";
                default -> null;
            };
            if (value != null) safe.add(Tag.of(tag.getKey(), value));
        }
        return new Meter.Id(id.getName(), io.micrometer.core.instrument.Tags.of(safe), null, null, id.getType());
    }
}
