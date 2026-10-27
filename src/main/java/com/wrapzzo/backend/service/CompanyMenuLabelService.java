package com.wrapzzo.backend.service;

import com.wrapzzo.backend.exception.InvalidMenuLabelException;
import com.wrapzzo.backend.model.CompanyMenuLabel;
import com.wrapzzo.backend.model.User;
import com.wrapzzo.backend.repository.CompanyMenuLabelRepository;
import com.wrapzzo.backend.repository.UserRepository;
import com.wrapzzo.backend.security.CurrentUser;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A company's own names for its navigation menus. The menu ids are the frontend's (its nav
 * config owns them), so this only checks their shape; an id the frontend no longer has is
 * simply never shown. Always the caller's own company.
 */
@Service
@RequiredArgsConstructor
public class CompanyMenuLabelService {

    static final int MAX_LABEL_LENGTH = 60;
    private static final Pattern MENU_ID = Pattern.compile("[a-z0-9][a-z0-9-]{0,63}");

    private final CompanyMenuLabelRepository companyMenuLabelRepository;
    private final UserRepository userRepository;

    /** Menu id -> the company's name for it; empty for a company that renamed nothing (or no company). */
    @Transactional(readOnly = true)
    public Map<String, String> labelsFor(UUID companyId) {
        if (companyId == null) {
            return Map.of();
        }
        return companyMenuLabelRepository.findById_CompanyId(companyId).stream()
                .collect(Collectors.toMap(l -> l.getId().getMenuId(), CompanyMenuLabel::getLabel, (a, b) -> a, TreeMap::new));
    }

    /** Replace-all for the caller's company: a blank name, or a menu left out, goes back to the default. */
    @Transactional
    public Map<String, String> replace(Map<String, String> labels) {
        UUID companyId = callerCompanyId();
        Map<String, String> cleaned = new TreeMap<>();
        labels.forEach((menuId, label) -> {
            if (menuId == null || !MENU_ID.matcher(menuId).matches()) {
                throw new InvalidMenuLabelException("Unknown menu: " + menuId);
            }
            String name = label == null ? "" : label.strip().replaceAll("\\s+", " ");
            if (name.length() > MAX_LABEL_LENGTH) {
                throw new InvalidMenuLabelException("A menu name can be at most " + MAX_LABEL_LENGTH + " characters: " + name);
            }
            if (!name.isEmpty()) {
                cleaned.put(menuId, name);
            }
        });
        companyMenuLabelRepository.deleteById_CompanyId(companyId);
        companyMenuLabelRepository.flush();
        cleaned.forEach((menuId, name) -> companyMenuLabelRepository.save(new CompanyMenuLabel(companyId, menuId, name)));
        return cleaned;
    }

    @Transactional(readOnly = true)
    public Map<String, String> listOwn() {
        return labelsFor(callerCompanyId());
    }

    private UUID callerCompanyId() {
        UUID userId = CurrentUser.id();
        User user = userRepository.findById(userId).orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + userId));
        if (user.getCompany() == null) {
            throw new InvalidMenuLabelException("Menu names belong to a company; platform staff have none");
        }
        return user.getCompany().getId();
    }
}
