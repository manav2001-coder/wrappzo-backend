package com.wrapzzo.backend.service;

import com.wrapzzo.backend.dto.CompanyRequest;
import com.wrapzzo.backend.dto.CompanyResponse;
import com.wrapzzo.backend.dto.PresignLogoUploadResponse;
import com.wrapzzo.backend.exception.CompanyNotFoundException;
import com.wrapzzo.backend.model.Company;
import com.wrapzzo.backend.model.CompanyStatus;
import com.wrapzzo.backend.model.Role;
import com.wrapzzo.backend.repository.CompanyRepository;
import com.wrapzzo.backend.repository.RoleRepository;
import com.wrapzzo.backend.repository.TemplateRepository;
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

    private static final Duration LOGO_UPLOAD_URL_EXPIRY = Duration.ofMinutes(5);

    private static final Map<String, String> EXTENSION_BY_CONTENT_TYPE = Map.of(
            "image/png", "png",
            "image/jpeg", "jpg",
            "image/jpg", "jpg",
            "image/webp", "webp",
            "image/svg+xml", "svg");

    private final CompanyRepository companyRepository;
    private final RoleRepository roleRepository;
    private final TemplateRepository templateRepository;
    private final FeatureService featureService;
    private final FileStorageService fileStorageService;

    @Transactional
    public CompanyResponse create(CompanyRequest request) {
        // saveAndFlush, not save -- companyNo is DB-generated (@Generated INSERT) and only
        // becomes readable on this entity once the physical INSERT actually runs, which a
        // plain save() defers until transaction commit, after the response below is built.
        Company company = new Company(request.name().trim());
        applyProfile(company, request);
        company = companyRepository.saveAndFlush(company);
        seedDefaultRoles(company);
        featureService.registerAll(company);
        return toResponse(company);
    }

    public List<CompanyResponse> list() {
        return companyRepository.findAll().stream().map(this::toResponse).toList();
    }

    public CompanyResponse get(UUID id) {
        return toResponse(getEntity(id));
    }

    @Transactional
    public CompanyResponse update(UUID id, CompanyRequest request) {
        Company company = getEntity(id);
        company.setName(request.name().trim());
        applyProfile(company, request);
        return toResponse(company);
    }

    private static void applyProfile(Company company, CompanyRequest request) {
        company.setContactPersonName(blankToNull(request.contactPersonName()));
        company.setContactEmail(blankToNull(request.contactEmail()));
        company.setContactMobile(blankToNull(request.contactMobile()));
        company.setGstin(blankToNull(request.gstin()));
        company.setPan(blankToNull(request.pan()));
        company.setRegistrationNumber(blankToNull(request.registrationNumber()));
        company.setAddressLine1(blankToNull(request.addressLine1()));
        company.setAddressLine2(blankToNull(request.addressLine2()));
        company.setCity(blankToNull(request.city()));
        company.setState(blankToNull(request.state()));
        company.setPincode(blankToNull(request.pincode()));
        company.setNotes(blankToNull(request.notes()));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
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

    /** A new company starts with one role, Company Admin, carrying the system master template of that name. */
    private void seedDefaultRoles(Company company) {
        Role role = new Role(company, RoleService.COMPANY_ADMIN_ROLE_NAME);
        templateRepository.findSystemByName(RoleService.COMPANY_ADMIN_ROLE_NAME).ifPresent(role.getTemplates()::add);
        roleRepository.save(role);
    }
}
