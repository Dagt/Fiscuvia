package architecture.fixtures.cyclic.second;

import architecture.fixtures.cyclic.first.First;

public record Second(First dependency) {}
