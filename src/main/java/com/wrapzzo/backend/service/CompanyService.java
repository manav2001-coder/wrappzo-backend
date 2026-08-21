package com.wrapzzo.backend.service;

import com.wrapzzo.backend.dto.CompanyResponse;
import com.wrapzzo.backend.dto.PresignLogoUploadResponse;
import com.wrapzzo.backend.exception.CompanyNotFoundException;
import com.wrapzzo.backend.model.AccessControl;
import com.wrapzzo.backend.model.Company;
import com.wrapzzo.backend.model.CompanyStatus;
import com.wrapzzo.backend.model.Role;
import com.wrapzzo.backend.model.RoleAccessControl;
import com.wrapzzo.backend.repository.AccessControlRepository;
import com.wrapzzo.backend.repository.CompanyRepository;
import com.wrapzzo.backend.repository.RoleAccessControlRepository;
import com.wrapzzo.backend.repository.RoleRepository;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CompanyService {

    /** Granted to every new company's auto-seeded "Company Admin" role. */
    private static final List<String> DEFAULT_COMPANY_ADMIN_ACCESS_KEYS = List.of("user.manage", "role.manage");

    private static final Duration LOGO_UPLOAD_URL_EXPIRY = Duration.ofMinutes(5);

    private static final Map<String, String> EXTENSION_BY_CONTENT_TYPE = Map.of(
            "image/png", "png",
            "image/jpeg", "jpg",
            "image/jpg", "jpg",
            "image/webp", "webp",
            "image/svg+xml", "svg");

    private final CompanyRepository companyRepository;
    private final RoleRepository roleRepository;
    private final AccessControlRepository accessControlRepository;
    private final RoleAccessControlRepository roleAccessControlRepository;
    private final FileStorageService fileStorageService;

    @Transactional
    public CompanyResponse create(String name) {
        // saveAndFlush, not save -- companyNo is DB-generated (@Generated INSERT) and only
        // becomes readable on this entity once the physical INSERT actually runs, which a
        // plain save() defers until transaction commit, after the response below is built.
        Company company = companyRepository.saveAndFlush(new Company(name));
        seedDefaultCompanyAdminRole(company);
        return toResponse(company);
    }

    public List<CompanyResponse> list() {
        return companyRepository.findAll().stream().map(this::toResponse).toList();
    }

    public CompanyResponse get(UUID id) {
        return toResponse(getEntity(id));
    }

    @Transactional
    public CompanyResponse update(UUID id, String name) {
        Company company = getEntity(id);
        company.setName(name);
        return toResponse(company);
    }

    @Transactional
    public CompanyResponse setStatus(UUID id, CompanyStatus status) {
        Company company = getEntity(id);
        company.setStatus(status);
        return toResponse(company);
    }

    public PresignLogoUploadResponse presignLogoUpload(UUID id, String contentType) {
        getEntity(id);
        String extension = EXTENSION_BY_CONTENT_TYPE.get(contentType);
        String key = "companies/%s/logo-%s.%s".formatted(id, UUID.randomUUID(), extension);
        String uploadUrl = fileStorageService.presignUpload(key, contentType, LOGO_UPLOAD_URL_EXPIRY);
        return new PresignLogoUploadResponse(uploadUrl, key);
    }

    @Transactional
    public CompanyResponse attachLogo(UUID id, String key) {
        Company company = getEntity(id);
        String previousKey = company.getLogoKey();
        company.setLogoKey(key);
        if (previousKey != null && !previousKey.equals(key)) {
            fileStorageService.delete(previousKey);
        }
        return toResponse(company);
    }

    private Company getEntity(UUID id) {
        return companyRepository.findById(id).orElseThrow(() -> new CompanyNotFoundException(id));
    }

    private CompanyResponse toResponse(Company company) {
        String logoUrl = company.getLogoKey() != null ? fileStorageService.publicUrl(company.getLogoKey()) : null;
        return CompanyResponse.from(company, logoUrl);
    }

    private void seedDefaultCompanyAdminRole(Company company) {
        Role role = roleRepository.save(new Role(company, "Company Admin"));
        for (String key : DEFAULT_COMPANY_ADMIN_ACCESS_KEYS) {
            AccessControl accessControl = accessControlRepository
                    .findByKey(key)
                    .orElseThrow(() -> new IllegalStateException("Missing seeded access control: " + key));
            roleAccessControlRepository.save(new RoleAccessControl(role, accessControl));
        }
    }
}
