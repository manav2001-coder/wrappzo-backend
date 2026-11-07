package com.wrapzzo.backend.list;

import com.fasterxml.jackson.annotation.JsonCreator;
import java.util.Arrays;
import java.util.Locale;

/** The filter vocabulary the screens already speak (lib/list-filter.ts), so a table's column filters travel unchanged. */
public enum FilterOperator {
    EQUALS, CONTAINS, IN, GTE, LTE, BETWEEN, IS_NULL;

    @JsonCreator
    public static FilterOperator fromJson(String value) {
        String normal = value == null ? "" : value.replace("_", "").toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(op -> op.name().replace("_", "").toLowerCase(Locale.ROOT).equals(normal))
                .findFirst()
                .orElseThrow(() -> new InvalidListQueryException("Unknown filter operator '" + value + "'."));
    }
}
