package com.wrapzzo.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** At startup, registers any catalogue feature a company lacks (see FeatureService.registerMissingForAllCompanies). */
@Slf4j
@Component
@RequiredArgsConstructor
public class CompanyFeatureRegistrar implements ApplicationRunner {

    private final FeatureService featureService;

    @Override
    public void run(ApplicationArguments args) {
        int added = featureService.registerMissingForAllCompanies();
        if (added > 0) {
            log.info("Registered {} missing feature(s) for existing companies", added);
        }
    }
}
