package com.wrapzzo.backend.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.wrapzzo.backend.model.GiftModule;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GiftResponseTest {

    @Test
    void aCompanysViewShowsThePriceItPaysButNotTheBasePriceOrThePlatformsMarkupAndCommission() {
        GiftResponse full = new GiftResponse(
                UUID.randomUUID(), "GFT-1", "Pad", "Stationery", null, 10, 5, true, false, false, Set.<GiftModule>of(),
                new BigDecimal("100.00"), new BigDecimal("10.00"), new BigDecimal("5.00"), new BigDecimal("115.00"),
                UUID.randomUUID(), "PrintHouse", List.of(), Instant.now());

        GiftResponse seen = full.forCompany();

        assertThat(seen.totalPrice()).isEqualByComparingTo("115.00");
        assertThat(seen.price()).isNull();
        assertThat(seen.markupFeePercentage()).isNull();
        assertThat(seen.commissionFeePercentage()).isNull();
        assertThat(seen.name()).isEqualTo("Pad");
        assertThat(seen.vendorName()).isEqualTo("PrintHouse");
    }
}
