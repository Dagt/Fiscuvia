package architecture.fixtures.illegal.consumer;

import architecture.fixtures.illegal.provider.internal.HiddenService;

public record IllegalConsumer(HiddenService dependency) {}
