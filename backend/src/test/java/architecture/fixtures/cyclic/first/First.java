package architecture.fixtures.cyclic.first;

import architecture.fixtures.cyclic.second.Second;

public record First(Second dependency) {}
