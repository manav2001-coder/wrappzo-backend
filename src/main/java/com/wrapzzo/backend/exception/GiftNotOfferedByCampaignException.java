package com.wrapzzo.backend.exception;

import java.util.UUID;

public class GiftNotOfferedByCampaignException extends RuntimeException {

    public GiftNotOfferedByCampaignException(UUID giftId) {
        super("This gift is not offered by the campaign: " + giftId);
    }
}
