package com.wrapzzo.backend.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Access control is enforced by the server, not just hidden in the screens: every endpoint must declare who may call it
 * (a permission key, a signed-in user whose right is decided on the record, or the vendor portal), except the few that are
 * public on purpose and answer to something else (signing in, the health check, the one-time photo-upload links). A new
 * endpoint without a guard fails this test, so it cannot ship open by accident.
 */
class EveryEndpointIsGuardedTest {

    /** Public on purpose: Controller#method. The link-based ones are authorised by their own single-use token. */
    private static final Set<String> PUBLIC_ON_PURPOSE = Set.of(
            "AuthController#login", "AuthController#refresh", "AuthController#logout", "AuthController#setupPassword",
            "VendorAuthController#login", "VendorAuthController#refresh", "VendorAuthController#logout", "VendorAuthController#setupPassword",
            "HealthController#health",
            "QuickGiftUploadController#details", "QuickGiftUploadController#presign", "QuickGiftUploadController#submit",
            "RecipientUploadController#details", "RecipientUploadController#presign", "RecipientUploadController#submit");

    @Test
    void everyEndpointDeclaresWhoMayCallIt_exceptTheFewPublicOnesOnPurpose() throws Exception {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));

        List<String> unguarded = new ArrayList<>();
        int endpoints = 0;
        for (BeanDefinition definition : scanner.findCandidateComponents("com.wrapzzo.backend.controller")) {
            Class<?> controller = Class.forName(definition.getBeanClassName());
            boolean classGuarded = AnnotatedElementUtils.findMergedAnnotation(controller, PreAuthorize.class) != null;
            for (Method method : controller.getDeclaredMethods()) {
                if (AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class) == null) {
                    continue;
                }
                endpoints++;
                boolean guarded = classGuarded || AnnotatedElementUtils.findMergedAnnotation(method, PreAuthorize.class) != null;
                String name = controller.getSimpleName() + "#" + method.getName();
                if (!guarded && !PUBLIC_ON_PURPOSE.contains(name)) {
                    unguarded.add(name);
                }
            }
        }

        assertThat(endpoints).as("the scan found the controllers").isGreaterThan(100);
        assertThat(unguarded).as("endpoints with no permission guard -- guard them, or list them as public on purpose with the reason").isEmpty();
    }

    @Test
    void theOnesLeftPublicAreStillUnderThePathsTheSecurityConfigOpensAndNothingElse() throws Exception {
        // The public list is not a place to park a forgotten guard: each must live under a path the security config deliberately opens.
        List<String> outside = new ArrayList<>();
        for (String name : PUBLIC_ON_PURPOSE) {
            Class<?> controller = Class.forName("com.wrapzzo.backend.controller." + name.substring(0, name.indexOf('#')));
            RequestMapping base = AnnotatedElementUtils.findMergedAnnotation(controller, RequestMapping.class);
            String path = base != null && base.value().length > 0 ? base.value()[0] : "";
            boolean opened = path.startsWith("/api/auth") || path.startsWith("/api/vendor/auth") || path.startsWith("/api/public") || path.startsWith("/api/health")
                    || controller.getSimpleName().equals("HealthController");
            if (!opened) {
                outside.add(name + " -> " + path);
            }
        }
        assertThat(outside).isEmpty();
    }
}
