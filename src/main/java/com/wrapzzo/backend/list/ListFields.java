package com.wrapzzo.backend.list;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * What a list can be searched, filtered and sorted by: an allow-list of named fields, each tied to one place in the
 * database. A request can name only these, so a client can never reach another column or table, and every field has
 * the type that decides how its filters are read. Built once per list (see {@link ListSpecs}).
 */
public final class ListFields<E> {

    public enum Type { TEXT, NUMBER, DATE, DAY, BOOLEAN, ENUM, UUID, CHOICE }

    /** Where a field lives. May join; {@link #join} reuses a join already made on the query. */
    @FunctionalInterface
    public interface Path<E> {
        Expression<?> of(Root<E> root, CriteriaBuilder cb);
    }

    /** A yes/no field that is not a column (an order with a proof waiting): the predicate for "wanted" true. */
    @FunctionalInterface
    public interface Flag<E> {
        Predicate holds(Root<E> root, CriteriaQuery<?> query, CriteriaBuilder cb);
    }

    /** A named value that is a rule, not a column (an order's stage): the predicate for one of its values, or null if there is no such value. */
    @FunctionalInterface
    public interface Choice<E> {
        Predicate holds(String value, Root<E> root, CriteriaQuery<?> query, CriteriaBuilder cb);
    }

    record Field<E>(String name, Type type, Class<? extends Enum<?>> enumType, Path<E> path, Flag<E> flag, Choice<E> choice, String sortPath, boolean searchable) {
    }

    private final Map<String, Field<E>> fields = new LinkedHashMap<>();

    public ListFields<E> text(String name, Path<E> path, String sortPath, boolean searchable) {
        return add(new Field<>(name, Type.TEXT, null, path, null, null, sortPath, searchable));
    }

    public ListFields<E> number(String name, Path<E> path, String sortPath) {
        return add(new Field<>(name, Type.NUMBER, null, path, null, null, sortPath, false));
    }

    public ListFields<E> date(String name, Path<E> path, String sortPath) {
        return add(new Field<>(name, Type.DATE, null, path, null, null, sortPath, false));
    }

    /** A calendar date with no time (a date of birth). */
    public ListFields<E> day(String name, Path<E> path, String sortPath) {
        return add(new Field<>(name, Type.DAY, null, path, null, null, sortPath, false));
    }

    public ListFields<E> bool(String name, Path<E> path, String sortPath) {
        return add(new Field<>(name, Type.BOOLEAN, null, path, null, null, sortPath, false));
    }

    public ListFields<E> enumeration(String name, Class<? extends Enum<?>> enumType, Path<E> path, String sortPath) {
        return add(new Field<>(name, Type.ENUM, enumType, path, null, null, sortPath, false));
    }

    /** An id column (a company, a vendor): matched exactly, never by text, so the index is used. */
    public ListFields<E> id(String name, Path<E> path, String sortPath) {
        return add(new Field<>(name, Type.UUID, null, path, null, null, sortPath, false));
    }

    /** A rule with named values (an order's stage): filtered with equals or in; not sortable. */
    public ListFields<E> choice(String name, Choice<E> choice) {
        return add(new Field<>(name, Type.CHOICE, null, null, null, choice, null, false));
    }

    /** A derived yes/no filter (not sortable, not searched). */
    public ListFields<E> flag(String name, Flag<E> flag) {
        return add(new Field<>(name, Type.BOOLEAN, null, null, flag, null, null, false));
    }

    private ListFields<E> add(Field<E> field) {
        fields.put(field.name(), field);
        return this;
    }

    Field<E> get(String name, String action) {
        Field<E> field = fields.get(name);
        if (field == null) {
            throw new InvalidListQueryException("Cannot " + action + " by '" + name + "'. You can " + action + " by: " + names(action) + ".");
        }
        return field;
    }

    Iterable<Field<E>> all() {
        return fields.values();
    }

    private String names(String action) {
        return fields.values().stream()
                .filter(f -> !"sort".equals(action) || f.sortPath() != null)
                .map(Field::name)
                .collect(Collectors.joining(", "));
    }

    /** A path to the named association, reusing a left join the query already made (so a filter and a search share one). */
    @SuppressWarnings("unchecked")
    public static <X, Y> Join<X, Y> join(From<?, X> from, String attribute) {
        for (Join<X, ?> existing : from.getJoins()) {
            if (existing.getAttribute().getName().equals(attribute) && existing.getJoinType() == JoinType.LEFT) {
                return (Join<X, Y>) existing;
            }
        }
        return from.join(attribute, JoinType.LEFT);
    }
}
