package com.wrapzzo.backend.controller;

import com.wrapzzo.backend.dto.ImportJobResponse;
import com.wrapzzo.backend.security.SignedInUser;
import com.wrapzzo.backend.service.ImportJobService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Where a bulk upload stands -- visible only to the person who uploaded it. */
@RestController
@RequestMapping("/api/import-jobs")
@RequiredArgsConstructor
public class ImportJobController {

    private final ImportJobService importJobs;

    @GetMapping("/{id}")
    @SignedInUser
    public ImportJobResponse get(@PathVariable UUID id) {
        return importJobs.get(id);
    }
}
