package com.wrapzzo.backend.service;

import com.wrapzzo.backend.dto.OrderApprovalSettingsRequest;
import com.wrapzzo.backend.dto.OrderApprovalSettingsResponse;
import com.wrapzzo.backend.exception.InvalidOrderApprovalSettingsException;
import com.wrapzzo.backend.model.Company;
import com.wrapzzo.backend.model.Role;
import com.wrapzzo.backend.model.User;
import com.wrapzzo.backend.repository.RoleRepository;
import com.wrapzzo.backend.repository.UserRepository;
import com.wrapzzo.backend.security.CurrentUser;
import java.util.Comparator;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The caller's own company's order approval: on or off, and who approves -- the company admin's to set. */
@Service
@RequiredArgsConstructor
public class OrderApprovalSettingsService {

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public OrderApprovalSettingsResponse get() {
        return respond(callersCompany());
    }

    /**
     * Applies to orders placed from now on; an order already waiting keeps waiting on whoever the new setup names, and one
     * placed while it was off is not pulled back.
     */
    @Transactional
    public OrderApprovalSettingsResponse set(OrderApprovalSettingsRequest request) {
        Company company = callersCompany();
        Role role = null;
        if (request.roleId() != null) {
            role = roleRepository.findById(request.roleId())
                    .filter(r -> r.isActive() && r.getCompany() != null && company.getId().equals(r.getCompany().getId()))
                    .orElseThrow(() -> new InvalidOrderApprovalSettingsException("Pick one of your company's active roles as the approver."));
        }
        if (request.enabled() && role == null) {
            throw new InvalidOrderApprovalSettingsException("Pick who approves orders before switching order approval on.");
        }
        company.setOrderApprovalEnabled(request.enabled());
        company.setOrderApprovalRole(role);
        company.setOrderApprovalScope(request.scope());
        return respond(company);
    }

    private OrderApprovalSettingsResponse respond(Company company) {
        Role role = company.getOrderApprovalRole();
        return new OrderApprovalSettingsResponse(
                company.isOrderApprovalEnabled(),
                role != null ? role.getId() : null,
                role != null ? role.getName() : null,
                company.getOrderApprovalScope(),
                roleRepository.findByCompanyId(company.getId()).stream()
                        .filter(Role::isActive)
                        .map(r -> new OrderApprovalSettingsResponse.RoleOption(r.getId(), r.getName()))
                        .sorted(Comparator.comparing(OrderApprovalSettingsResponse.RoleOption::name, String.CASE_INSENSITIVE_ORDER))
                        .toList());
    }

    private Company callersCompany() {
        User user = userRepository.findById(CurrentUser.id()).orElseThrow(() -> new IllegalStateException("Authenticated user not found"));
        if (user.getCompany() == null) {
            throw new AccessDeniedException("Order approval belongs to a company; platform staff have none.");
        }
        return user.getCompany();
    }
}
