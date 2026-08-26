package com.wrapzzo.backend.controller;

import com.wrapzzo.backend.dto.AccessControlResponse;
import com.wrapzzo.backend.repository.AccessControlRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The full, fixed catalog of grantable capabilities -- read-only, for building a designation's grant checklist. */
@RestController
@RequestMapping("/api/access-controls")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('role.manage')")
public class AccessControlController {

    private final AccessControlRepository accessControlRepository;

    @GetMapping
    public List<AccessControlResponse> list() {
        return accessControlRepository.findByCompanyAssignableTrue().stream().map(AccessControlResponse::from).toList();
    }
}
