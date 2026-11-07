package com.wrapzzo.backend.list;

/**
 * The query string of a list endpoint: {@code page} (from 0), {@code size}, {@code search}, {@code sort}, {@code dir} (asc or desc)
 * and {@code filters} (a JSON array of {field, operator, value}). Bound by Spring and turned into a {@link ListQuery}.
 */
public record ListQueryParams(Integer page, Integer size, String search, String sort, String dir, String filters) {

    public ListQuery toQuery() {
        return ListQuery.parse(this);
    }
}
