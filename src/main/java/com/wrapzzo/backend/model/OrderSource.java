package com.wrapzzo.backend.model;

/** Which commerce path created this Order -- all three share the same Order engine (PRD 2.10). */
public enum OrderSource {
    CAMPAIGN,
    QUICK_GIFT,
    BULK_GIFT
}
