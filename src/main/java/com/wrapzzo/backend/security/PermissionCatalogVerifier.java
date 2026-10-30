package com.wrapzzo.backend.security;

import com.wrapzzo.backend.model.AccessControl;
import com.wrapzzo.backend.model.AccessControlType;
import com.wrapzzo.backend.repository.AccessControlRepository;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fails startup when the permission catalogue in code (Permission) and in the database
 * (access_controls, seeded by migrations) disagree -- a key one side has and the other doesn't,
 * or a different company-assignable flag. Better a failed deploy (the previous version keeps
 * serving) than endpoints guarded by a key nobody can be granted.
 */
@Component
@RequiredArgsConstructor
public class PermissionCatalogVerifier implements ApplicationRunner {

    private final AccessControlRepository accessControlRepository;

    @Override
    @Transactional(readOnly = true)
    public void run(ApplicationArguments args) {
        Map<String, AccessControl> inDatabase = accessControlRepository.findByType(AccessControlType.FUNCTIONAL).stream()
                .collect(Collectors.toMap(AccessControl::getKey, Function.identity()));
        Set<String> inCode = Arrays.stream(Permission.values()).map(Permission::key).collect(Collectors.toSet());

        Set<String> problems = new TreeSet<>();
        inCode.stream().filter(key -> !inDatabase.containsKey(key)).forEach(key -> problems.add("missing in access_controls: " + key));
        inDatabase.keySet().stream().filter(key -> !inCode.contains(key)).forEach(key -> problems.add("not in Permission: " + key));
        for (Permission permission : Permission.values()) {
            AccessControl row = inDatabase.get(permission.key());
            if (row != null && row.isCompanyAssignable() != permission.companyAssignable()) {
                problems.add("company_assignable differs for " + permission.key());
            }
        }
        if (!problems.isEmpty()) {
            throw new IllegalStateException("Permission catalogue out of sync -- add a migration or fix Permission: " + problems);
        }
    }
}
