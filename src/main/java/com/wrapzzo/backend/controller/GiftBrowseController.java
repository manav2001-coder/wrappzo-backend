package com.wrapzzo.backend.controller;

import com.wrapzzo.backend.dto.GiftResponse;
import com.wrapzzo.backend.service.GiftService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The Gift Store -- any authenticated company user, active gifts only. No gift.manage required. */
@RestController
@RequestMapping("/api/gifts")
@RequiredArgsConstructor
public class GiftBrowseController {

    private final GiftService giftService;

    @GetMapping("/list")
    public List<GiftResponse> list() {
        return giftService.listActive();
    }

    @GetMapping("/view/{id}")
    public GiftResponse view(@PathVariable UUID id) {
        return giftService.getActive(id);
    }
}
