package com.wrapzzo.backend.list;

import com.fasterxml.jackson.databind.JsonNode;

/** One column filter: a field, an operator and (for most operators) a value. */
public record ListFilter(String field, FilterOperator operator, JsonNode value) {
}
