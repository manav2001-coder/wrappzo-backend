package com.wrapzzo.backend.list;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * The same search, filters, sort and paging as {@link ListSpecs}, for a list that cannot be answered by one database query because
 * who may act on a row is worked out per row (an approval queue). It runs over rows already narrowed in the database to the ones
 * in the caller's reach, so it is bounded by what is waiting, not by how much has ever happened -- and the screen still gets a page.
 */
public final class ListInMemory<T> {

    private final Map<String, Column<T>> columns = new LinkedHashMap<>();

    private record Column<T>(Function<T, Object> value, boolean searchable) {
    }

    public ListInMemory<T> column(String name, Function<T, Object> value, boolean searchable) {
        columns.put(name, new Column<>(value, searchable));
        return this;
    }

    public PageResponse<T> page(List<T> rows, ListQuery query, Comparator<T> defaultOrder) {
        List<T> matches = new ArrayList<>();
        for (T row : rows) {
            if (matchesSearch(row, query) && query.filters().stream().allMatch(filter -> matches(row, filter))) {
                matches.add(row);
            }
        }
        matches.sort(orderOf(query, defaultOrder));
        int from = (int) Math.min((long) query.page() * query.size(), matches.size());
        int to = Math.min(from + query.size(), matches.size());
        return new PageResponse<>(List.copyOf(matches.subList(from, to)), matches.size(), query.page(), query.size(), to < matches.size());
    }

    private Comparator<T> orderOf(ListQuery query, Comparator<T> defaultOrder) {
        if (query.sort() == null) {
            return defaultOrder;
        }
        Function<T, Object> value = column(query.sort(), "sort").value();
        Comparator<T> byValue = (a, b) -> compare(value.apply(a), value.apply(b));
        return query.descending() ? byValue.reversed() : byValue;
    }

    private boolean matchesSearch(T row, ListQuery query) {
        if (query.search() == null) {
            return true;
        }
        String needle = query.search().toLowerCase(Locale.ROOT);
        return columns.values().stream().filter(Column::searchable)
                .anyMatch(column -> text(column.value().apply(row)).contains(needle));
    }

    private boolean matches(T row, ListFilter filter) {
        Object cell = column(filter.field(), "filter").value().apply(row);
        return switch (filter.operator()) {
            case IS_NULL -> cell == null || text(cell).isBlank();
            case EQUALS -> cell instanceof Number ? compare(cell, number(filter.value())) == 0 : text(cell).equals(single(filter).toLowerCase(Locale.ROOT));
            case CONTAINS -> text(cell).contains(single(filter).toLowerCase(Locale.ROOT));
            case IN -> many(filter).stream().anyMatch(item -> text(cell).equals(item.asText().toLowerCase(Locale.ROOT)));
            case GTE -> compare(cell, comparable(cell, filter.value())) >= 0;
            case LTE -> compare(cell, comparable(cell, filter.value())) <= 0;
            case BETWEEN -> compare(cell, comparable(cell, many(filter).get(0))) >= 0 && compare(cell, comparable(cell, many(filter).get(many(filter).size() - 1))) <= 0;
        };
    }

    private Column<T> column(String name, String action) {
        Column<T> column = columns.get(name);
        if (column == null) {
            throw new InvalidListQueryException("Cannot " + action + " by '" + name + "'. You can " + action + " by: " + String.join(", ", columns.keySet()) + ".");
        }
        return column;
    }

    private static Object comparable(Object cell, JsonNode value) {
        if (cell instanceof Number) {
            return number(value);
        }
        if (cell instanceof Instant) {
            try {
                String text = value.asText().trim();
                return text.length() <= 10 ? java.time.LocalDate.parse(text).atStartOfDay(ListSpecs.ZONE).toInstant() : ListSpecs.timestamp(text);
            } catch (java.time.format.DateTimeParseException e) {
                throw new InvalidListQueryException("'" + value.asText() + "' is not a date (use yyyy-MM-dd).");
            }
        }
        return value.asText().toLowerCase(Locale.ROOT);
    }

    private static BigDecimal number(JsonNode value) {
        try {
            return new BigDecimal(value.asText().trim());
        } catch (NumberFormatException e) {
            throw new InvalidListQueryException("'" + value.asText() + "' is not a number.");
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static int compare(Object a, Object b) {
        if (a == null || b == null) {
            return a == null ? (b == null ? 0 : -1) : 1;
        }
        if (a instanceof Number x && b instanceof Number y) {
            return new BigDecimal(x.toString()).compareTo(new BigDecimal(y.toString()));
        }
        if (a instanceof Comparable && a.getClass().isInstance(b)) {
            return ((Comparable) a).compareTo(b);
        }
        return text(a).compareTo(text(b));
    }

    private static String text(Object value) {
        return value == null ? "" : value.toString().toLowerCase(Locale.ROOT);
    }

    private static String single(ListFilter filter) {
        if (filter.value() == null || filter.value().isContainerNode() || filter.value().isNull()) {
            throw new InvalidListQueryException("The filter on '" + filter.field() + "' needs a single value.");
        }
        return filter.value().asText();
    }

    private static List<JsonNode> many(ListFilter filter) {
        if (filter.value() == null || !filter.value().isArray() || filter.value().isEmpty()) {
            throw new InvalidListQueryException("The filter on '" + filter.field() + "' needs a list of values.");
        }
        List<JsonNode> items = new ArrayList<>();
        filter.value().forEach(items::add);
        return items;
    }
}
