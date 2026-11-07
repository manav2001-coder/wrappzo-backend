package com.wrapzzo.backend.list;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/** One page of a list, in the shape the screens already read (lib/list-filter.ts Paginated). */
public record PageResponse<T>(List<T> data, long total, int page, int pageSize, boolean hasMore) {

    public <R> PageResponse<R> map(Function<List<T>, List<R>> mapper) {
        return new PageResponse<>(mapper.apply(data), total, page, pageSize, hasMore);
    }

    public static <E, R> PageResponse<R> of(Page<E> page, Function<List<E>, List<R>> mapper) {
        return new PageResponse<>(mapper.apply(page.getContent()), page.getTotalElements(), page.getNumber(), page.getSize(), page.hasNext());
    }
}
