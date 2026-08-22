package com.wrapzzo.backend.service;

import com.wrapzzo.backend.dto.OrgUnitResponse;
import com.wrapzzo.backend.repository.OrgUnitRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only for now -- org units are created implicitly by bulk user import, not managed directly yet. */
@Service
@RequiredArgsConstructor
public class OrgUnitService {

    private final OrgUnitRepository orgUnitRepository;

    // readOnly transaction, not a plain read -- OrgUnitResponse.from touches the lazy
    // parent association (see GiftService/UserService's own doc comments on this exact bug).
    @Transactional(readOnly = true)
    public List<OrgUnitResponse> listByCompany(UUID companyId) {
        return orgUnitRepository.findByCompanyId(companyId).stream().map(OrgUnitResponse::from).toList();
    }
}
