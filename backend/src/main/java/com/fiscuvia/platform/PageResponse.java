package com.fiscuvia.platform;

import java.util.List;

/** No automatic count query or tenant scope. Repositories must apply authorization before paging. */
public record PageResponse<T>(List<T> items, int page, int size, boolean hasNext) {
    public PageResponse {
        items = List.copyOf(items);
        if (page < 0 || page > 10000 || size < 1 || size > 100 || items.size() > size) {
            throw new IllegalArgumentException("Invalid page response configuration");
        }
    }
}
