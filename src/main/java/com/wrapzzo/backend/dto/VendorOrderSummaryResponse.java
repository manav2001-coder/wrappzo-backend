package com.wrapzzo.backend.dto;

import java.util.Map;

/** The figures above the vendor's Orders list, counted in the database: how many orders are at each stage, whatever page the list is on. */
public record VendorOrderSummaryResponse(long total, Map<String, Long> byStage) {
}
