package com.wrapzzo.backend.service;

import com.wrapzzo.backend.dto.AccessControlResponse;
import com.wrapzzo.backend.repository.AccessControlRepository;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The permissions a company's designations can be given, in access-grid order. */
@Service
@RequiredArgsConstructor
public class AccessControlCatalogService {

    private final AccessControlRepository accessControlRepository;

    @Transactional(readOnly = true)
    public List<AccessControlResponse> listCompanyAssignable() {
        return accessControlRepository.findByCompanyAssignableTrue().stream()
                .sorted(Comparator.comparingInt(ac -> ac.getSortOrder()))
                .map(AccessControlResponse::from)
                .toList();
    }
}
