package com.wrapzzo.backend.list;

import com.fasterxml.jackson.databind.JsonNode;
import com.wrapzzo.backend.list.ListFields.Field;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * Turns a {@link ListQuery} into one database query: the search, the column filters and the sort, all against the
 * {@link ListFields} allow-list, on top of a scope the caller has already narrowed to what the user may see. Paging happens in the
 * database, so a list costs one page, not the whole table.
 */
public final class ListSpecs {

    /** Dates are read in the users' day, so "to 6 Nov" includes everything placed on the 6th in India. */
    static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");

    private ListSpecs() {
    }

    /** One page of {@code scope} narrowed by the query, each row turned into its response in one batch (so lookups are per page, not per row). */
    public static <E, R> PageResponse<R> page(JpaSpecificationExecutor<E> repository, Specification<E> scope, ListQuery query, ListFields<E> fields,
            Sort defaultSort, Function<List<E>, List<R>> toResponses) {
        Pageable pageable = PageRequest.of(query.page(), query.size(), sortOf(query, fields, defaultSort));
        return PageResponse.of(repository.findAll(narrow(scope, query, fields), pageable), toResponses);
    }

    /** {@code scope} with the query's search and filters applied -- also what a summary counts over. */
    public static <E> Specification<E> narrow(Specification<E> scope, ListQuery query, ListFields<E> fields) {
        Specification<E> narrowed = scope;
        for (ListFilter filter : query.filters()) {
            narrowed = narrowed.and(filterSpec(fields.get(filter.field(), "filter"), filter));
        }
        if (query.search() != null) {
            narrowed = narrowed.and(searchSpec(query.search(), fields));
        }
        return narrowed;
    }

    static <E> Sort sortOf(ListQuery query, ListFields<E> fields, Sort defaultSort) {
        if (query.sort() == null) {
            return defaultSort.and(Sort.by("id"));
        }
        Field<E> field = fields.get(query.sort(), "sort");
        if (field.sortPath() == null) {
            throw new InvalidListQueryException("Cannot sort by '" + query.sort() + "'.");
        }
        Sort.Order order = query.descending() ? Sort.Order.desc(field.sortPath()) : Sort.Order.asc(field.sortPath());
        if (field.type() == ListFields.Type.TEXT) {
            order = order.ignoreCase();
        }
        return Sort.by(order).and(Sort.by("id"));
    }

    private static <E> Specification<E> searchSpec(String text, ListFields<E> fields) {
        return (root, query, cb) -> {
            String pattern = like(text);
            List<Predicate> any = new ArrayList<>();
            for (Field<E> field : fields.all()) {
                if (field.searchable()) {
                    any.add(cb.like(cb.lower(field.path().of(root, cb).as(String.class)), pattern, '\\'));
                }
            }
            return any.isEmpty() ? cb.conjunction() : cb.or(any.toArray(Predicate[]::new));
        };
    }

    private static <E> Specification<E> filterSpec(Field<E> field, ListFilter filter) {
        return (root, query, cb) -> {
            if (field.flag() != null) {
                return flagPredicate(field, filter, root, query, cb);
            }
            Expression<?> expression = field.path().of(root, cb);
            return switch (field.type()) {
                case TEXT -> text(expression.as(String.class), filter, cb);
                case NUMBER -> number(expression, filter, cb);
                case DATE -> date(expression, filter, cb);
                case DAY -> day(expression, filter, cb);
                case BOOLEAN -> bool(expression, filter, cb);
                case ENUM -> enumeration(field, expression, filter, cb);
                case UUID -> uuid(field, expression, filter, cb);
            };
        };
    }

    private static <E> Predicate flagPredicate(Field<E> field, ListFilter filter, Root<E> root, jakarta.persistence.criteria.CriteriaQuery<?> query, CriteriaBuilder cb) {
        if (filter.operator() != FilterOperator.EQUALS) {
            throw new InvalidListQueryException("'" + field.name() + "' can only be filtered with equals true or false.");
        }
        Predicate holds = field.flag().holds(root, query, cb);
        return wantedBoolean(filter, field.name()) ? holds : cb.not(holds);
    }

    // --- by type ---

    private static Predicate text(Expression<String> value, ListFilter filter, CriteriaBuilder cb) {
        Expression<String> lower = cb.lower(value);
        return switch (filter.operator()) {
            case IS_NULL -> cb.or(cb.isNull(value), cb.equal(cb.trim(value), ""));
            case EQUALS -> cb.equal(lower, string(filter).toLowerCase(Locale.ROOT));
            case CONTAINS -> cb.like(lower, like(string(filter)), '\\');
            case IN -> lower.in(strings(filter).stream().map(s -> s.toLowerCase(Locale.ROOT)).toList());
            case GTE -> cb.greaterThanOrEqualTo(lower, string(filter).toLowerCase(Locale.ROOT));
            case LTE -> cb.lessThanOrEqualTo(lower, string(filter).toLowerCase(Locale.ROOT));
            case BETWEEN -> cb.between(lower, strings(filter).get(0).toLowerCase(Locale.ROOT), pair(filter, 1).toLowerCase(Locale.ROOT));
        };
    }

    private static Predicate number(Expression<?> value, ListFilter filter, CriteriaBuilder cb) {
        Expression<BigDecimal> number = value.as(BigDecimal.class);
        return switch (filter.operator()) {
            case IS_NULL -> cb.isNull(value);
            case EQUALS -> cb.equal(number, decimal(filter.value()));
            case CONTAINS -> cb.like(value.as(String.class), like(string(filter)), '\\');
            case IN -> number.in(items(filter).stream().map(ListSpecs::decimal).toList());
            case GTE -> cb.greaterThanOrEqualTo(number, decimal(filter.value()));
            case LTE -> cb.lessThanOrEqualTo(number, decimal(filter.value()));
            case BETWEEN -> cb.between(number, decimal(items(filter).get(0)), decimal(items(filter).get(pairIndex(filter))));
        };
    }

    @SuppressWarnings("unchecked")
    private static Predicate date(Expression<?> value, ListFilter filter, CriteriaBuilder cb) {
        Expression<Instant> at = (Expression<Instant>) value;
        return switch (filter.operator()) {
            case IS_NULL -> cb.isNull(at);
            case EQUALS -> cb.and(cb.greaterThanOrEqualTo(at, from(filter.value())), cb.lessThan(at, until(filter.value())));
            case GTE -> cb.greaterThanOrEqualTo(at, from(filter.value()));
            case LTE -> cb.lessThan(at, until(filter.value()));
            case BETWEEN -> cb.and(cb.greaterThanOrEqualTo(at, from(items(filter).get(0))), cb.lessThan(at, until(items(filter).get(pairIndex(filter)))));
            case CONTAINS, IN -> throw new InvalidListQueryException("A date can be filtered with equals, gte, lte or between.");
        };
    }

    @SuppressWarnings("unchecked")
    private static Predicate day(Expression<?> value, ListFilter filter, CriteriaBuilder cb) {
        Expression<LocalDate> on = (Expression<LocalDate>) value;
        return switch (filter.operator()) {
            case IS_NULL -> cb.isNull(on);
            case EQUALS -> cb.equal(on, localDate(filter.value()));
            case GTE -> cb.greaterThanOrEqualTo(on, localDate(filter.value()));
            case LTE -> cb.lessThanOrEqualTo(on, localDate(filter.value()));
            case BETWEEN -> cb.between(on, localDate(items(filter).get(0)), localDate(items(filter).get(pairIndex(filter))));
            case CONTAINS, IN -> throw new InvalidListQueryException("A date can be filtered with equals, gte, lte or between.");
        };
    }

    private static LocalDate localDate(JsonNode node) {
        try {
            return LocalDate.parse(node.asText().trim().substring(0, Math.min(10, node.asText().trim().length())));
        } catch (DateTimeParseException e) {
            throw new InvalidListQueryException("'" + node.asText() + "' is not a date (use yyyy-MM-dd).");
        }
    }

    @SuppressWarnings("unchecked")
    private static Predicate bool(Expression<?> value, ListFilter filter, CriteriaBuilder cb) {
        if (filter.operator() == FilterOperator.IS_NULL) {
            return cb.isNull(value);
        }
        if (filter.operator() != FilterOperator.EQUALS) {
            throw new InvalidListQueryException("A yes/no field can only be filtered with equals true or false.");
        }
        return cb.equal((Expression<Boolean>) value, wantedBoolean(filter, "this field"));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Predicate enumeration(Field<?> field, Expression<?> value, ListFilter filter, CriteriaBuilder cb) {
        if (filter.operator() == FilterOperator.IS_NULL) {
            return cb.isNull(value);
        }
        Class<? extends Enum> type = (Class<? extends Enum>) field.enumType();
        List<Enum> wanted = (filter.operator() == FilterOperator.IN ? strings(filter) : List.of(string(filter))).stream()
                .map(s -> enumValue(type, s, field.name()))
                .toList();
        if (filter.operator() != FilterOperator.EQUALS && filter.operator() != FilterOperator.IN) {
            throw new InvalidListQueryException("'" + field.name() + "' can be filtered with equals or in.");
        }
        return filter.operator() == FilterOperator.EQUALS ? cb.equal(value, wanted.get(0)) : value.in(wanted);
    }

    @SuppressWarnings("unchecked")
    private static Predicate uuid(Field<?> field, Expression<?> value, ListFilter filter, CriteriaBuilder cb) {
        Expression<java.util.UUID> id = (Expression<java.util.UUID>) value;
        return switch (filter.operator()) {
            case IS_NULL -> cb.isNull(id);
            case EQUALS -> cb.equal(id, uuidOf(string(filter), field.name()));
            case IN -> id.in(strings(filter).stream().map(s -> uuidOf(s, field.name())).toList());
            default -> throw new InvalidListQueryException("'" + field.name() + "' can be filtered with equals or in.");
        };
    }

    private static java.util.UUID uuidOf(String text, String field) {
        try {
            return java.util.UUID.fromString(text.trim());
        } catch (IllegalArgumentException e) {
            throw new InvalidListQueryException("'" + text + "' is not a valid id for '" + field + "'.");
        }
    }

    // --- reading values ---

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Enum enumValue(Class<? extends Enum> type, String text, String field) {
        try {
            return Enum.valueOf(type, text.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new InvalidListQueryException("'" + text + "' is not a value of '" + field + "'.");
        }
    }

    private static String string(ListFilter filter) {
        JsonNode value = filter.value();
        if (value == null || value.isNull() || value.isContainerNode()) {
            throw new InvalidListQueryException("The filter on '" + filter.field() + "' needs a single value.");
        }
        return value.asText();
    }

    private static List<String> strings(ListFilter filter) {
        return items(filter).stream().map(JsonNode::asText).toList();
    }

    private static String pair(ListFilter filter, int index) {
        return strings(filter).get(index);
    }

    private static List<JsonNode> items(ListFilter filter) {
        JsonNode value = filter.value();
        if (value == null || !value.isArray() || value.isEmpty()) {
            throw new InvalidListQueryException("The filter on '" + filter.field() + "' needs a list of values.");
        }
        if (filter.operator() == FilterOperator.BETWEEN && value.size() != 2) {
            throw new InvalidListQueryException("The 'between' filter on '" + filter.field() + "' needs exactly two values.");
        }
        List<JsonNode> items = new ArrayList<>();
        value.forEach(items::add);
        return items;
    }

    private static int pairIndex(ListFilter filter) {
        return items(filter).size() - 1;
    }

    private static boolean wantedBoolean(ListFilter filter, String name) {
        JsonNode value = filter.value();
        if (value == null || !(value.isBoolean() || "true".equalsIgnoreCase(value.asText()) || "false".equalsIgnoreCase(value.asText()))) {
            throw new InvalidListQueryException("'" + name + "' needs true or false.");
        }
        return value.asBoolean() || "true".equalsIgnoreCase(value.asText());
    }

    private static BigDecimal decimal(JsonNode node) {
        try {
            return new BigDecimal(node.asText().trim());
        } catch (NumberFormatException e) {
            throw new InvalidListQueryException("'" + node.asText() + "' is not a number.");
        }
    }

    /** The start of the day for a date (yyyy-MM-dd), or the instant itself for a full timestamp. */
    private static Instant from(JsonNode node) {
        String text = node.asText().trim();
        try {
            return text.length() <= 10 ? LocalDate.parse(text).atStartOfDay(ZONE).toInstant() : timestamp(text);
        } catch (DateTimeParseException e) {
            throw new InvalidListQueryException("'" + text + "' is not a date (use yyyy-MM-dd).");
        }
    }

    /** A timestamp with a Z or an offset (+05:30), as browsers and APIs write them. */
    static Instant timestamp(String text) {
        try {
            return Instant.parse(text);
        } catch (DateTimeParseException e) {
            return java.time.OffsetDateTime.parse(text).toInstant();
        }
    }

    /** The end of that day, exclusive -- so "to 6 Nov" takes in all of the 6th. A full timestamp stands for itself (plus a moment). */
    private static Instant until(JsonNode node) {
        String text = node.asText().trim();
        try {
            return text.length() <= 10 ? LocalDate.parse(text).plusDays(1).atStartOfDay(ZONE).toInstant() : timestamp(text).plusMillis(1);
        } catch (DateTimeParseException e) {
            throw new InvalidListQueryException("'" + text + "' is not a date (use yyyy-MM-dd).");
        }
    }

    /** {@code %text%} with the characters LIKE treats as wildcards escaped, so a search for "50%" finds "50%". */
    private static String like(String text) {
        return "%" + text.toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }
}
