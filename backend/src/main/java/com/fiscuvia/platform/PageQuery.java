package com.fiscuvia.platform;

import java.util.Set;

/** Endpoint-owned sort allowlist; never pass the raw request to SQL or an ORM. */
public record PageQuery(int page, int size, String sort, Direction direction) {
    public enum Direction { ASC, DESC }

    public PageQuery {
        if (page < 0 || page > 10000 || size < 1 || size > 100 || sort == null
                || !sort.matches("[a-zA-Z][a-zA-Z0-9]{0,63}") || direction == null) {
            throw new ApiFailure(ApiErrorCode.INVALID_PAGINATION);
        }
    }

    public static PageQuery parse(Integer page, Integer size, String sort, Set<String> allowedFields) {
        String[] order = (sort == null ? "id,asc" : sort).split(",", -1);
        if (order.length != 2 || !allowedFields.contains(order[0])
                || !(order[1].equals("asc") || order[1].equals("desc"))) {
            throw new ApiFailure(ApiErrorCode.INVALID_PAGINATION);
        }
        return new PageQuery(page == null ? 0 : page, size == null ? 20 : size,
                order[0], order[1].equals("asc") ? Direction.ASC : Direction.DESC);
    }

    public long offset() { return (long) page * size; }
}
