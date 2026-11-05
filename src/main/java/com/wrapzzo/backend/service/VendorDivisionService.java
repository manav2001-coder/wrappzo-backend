package com.wrapzzo.backend.service;

import com.wrapzzo.backend.dto.VendorDivisionResponse;
import com.wrapzzo.backend.model.CompanyVendor;
import com.wrapzzo.backend.model.OrgUnit;
import com.wrapzzo.backend.repository.CompanyVendorRepository;
import com.wrapzzo.backend.repository.OrgUnitRepository;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The divisions a vendor serves, from how the company mapped it: a whole-company mapping serves every division, a mapping to
 * one unit serves that unit's division. This is what the vendor portal's division filter offers, so a vendor mapped to two
 * divisions can pick either even before an order has come from the second.
 */
@Service
@RequiredArgsConstructor
public class VendorDivisionService {

    private final CompanyVendorRepository companyVendorRepository;
    private final OrgUnitRepository orgUnitRepository;

    @Transactional(readOnly = true)
    public List<VendorDivisionResponse> servedDivisions(UUID vendorId) {
        Map<UUID, OrgUnit> divisions = new LinkedHashMap<>();
        for (CompanyVendor mapping : companyVendorRepository.findByVendor_Id(vendorId)) {
            OrgUnit unit = mapping.getOrgUnit();
            if (unit == null) {
                orgUnitRepository.findByCompanyId(mapping.getCompany().getId()).stream()
                        .filter(candidate -> candidate.getAncestorIds().isEmpty() && candidate.isActive())
                        .forEach(division -> divisions.putIfAbsent(division.getId(), division));
            } else {
                OrgUnit division = unit.getAncestorIds().isEmpty() ? unit : orgUnitRepository.findById(unit.getAncestorIds().get(0)).orElse(unit);
                divisions.putIfAbsent(division.getId(), division);
            }
        }
        return divisions.values().stream()
                .sorted(Comparator.comparing(OrgUnit::getName, String.CASE_INSENSITIVE_ORDER))
                .map(division -> new VendorDivisionResponse(division.getId(), division.getName()))
                .toList();
    }
}
