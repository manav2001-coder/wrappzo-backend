package com.wrapzzo.backend.service;

import com.wrapzzo.backend.model.User;
import com.wrapzzo.backend.repository.UserRepository;
import com.wrapzzo.backend.security.CurrentUser;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

/**
 * Guards the /api/platform/companies/{companyId}/** endpoints that are reachable by both
 * the real platform Super Admin (company_id null) and a company's own Company Admin, who
 * now also holds keys like user.manage. A bare @PreAuthorize("hasAuthority('user.manage')")
 * on those controllers isn't enough on its own once a company-scoped role holds the same
 * key -- without this check, a Company Admin could reach a DIFFERENT company's users/org
 * units just by changing the URL's companyId. Company_id null on the caller is the same
 * "platform staff" signal used everywhere else in this schema (see the users table's own
 * doc comment), so no separate platformSuperAdmin flag lookup is needed here.
 */
@Service
@RequiredArgsConstructor
public class CompanyAccessGuard {

    private final UserRepository userRepository;

    public void requireOwnCompanyOrPlatformStaff(UUID companyId) {
        UUID callerId = CurrentUser.id();
        User caller = userRepository.findById(callerId).orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + callerId));
        boolean isPlatformStaff = caller.getCompany() == null;
        if (!isPlatformStaff && !caller.getCompany().getId().equals(companyId)) {
            throw new AccessDeniedException("Not authorized for this company");
        }
    }
}
