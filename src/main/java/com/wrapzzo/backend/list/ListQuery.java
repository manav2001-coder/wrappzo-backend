package com.wrapzzo.backend.list;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;

/** A validated list request. Page size is capped so no screen can ask for the whole table in one go. */
public record ListQuery(int page, int size, String search, String sort, boolean descending, List<ListFilter> filters) {

    public static final int DEFAULT_SIZE = 25;
    public static final int MAX_SIZE = 100;
    static final int MAX_FILTERS = 20;
    static final int MAX_SEARCH_LENGTH = 100;
    private static final ObjectMapper JSON = new ObjectMapper();

    static ListQuery parse(ListQueryParams params) {
        int page = params.page() == null ? 0 : params.page();
        if (page < 0) {
            throw new InvalidListQueryException("The page number cannot be negative.");
        }
        int size = params.size() == null ? DEFAULT_SIZE : Math.min(Math.max(params.size(), 1), MAX_SIZE);
        String search = params.search() == null ? null : params.search().trim();
        if (search != null && search.length() > MAX_SEARCH_LENGTH) {
            throw new InvalidListQueryException("The search text can be at most " + MAX_SEARCH_LENGTH + " characters.");
        }
        List<ListFilter> filters = List.of();
        if (params.filters() != null && !params.filters().isBlank()) {
            try {
                filters = JSON.readValue(params.filters(), new TypeReference<List<ListFilter>>() {});
            } catch (JsonProcessingException | InvalidListQueryException e) {
                throw new InvalidListQueryException("The filters could not be read: " + (e instanceof InvalidListQueryException ? e.getMessage() : "not a valid list of {field, operator, value}."));
            }
            if (filters.size() > MAX_FILTERS) {
                throw new InvalidListQueryException("At most " + MAX_FILTERS + " filters can be applied at once.");
            }
        }
        String sort = params.sort() == null || params.sort().isBlank() ? null : params.sort().trim();
        return new ListQuery(page, size, search == null || search.isEmpty() ? null : search, sort, "desc".equalsIgnoreCase(params.dir()), filters);
    }

    /** A request for the first {@code size} rows with nothing applied. */
    public static ListQuery firstPage(int size) {
        return new ListQuery(0, Math.min(size, MAX_SIZE), null, null, false, List.of());
    }
}
