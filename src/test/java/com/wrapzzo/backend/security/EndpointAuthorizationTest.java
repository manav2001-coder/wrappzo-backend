package com.wrapzzo.backend.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Every endpoint states who may call it: a permission (@RequiresPermission or @PreAuthorize),
 * @SignedInUser / @VendorEndpoint, or @PublicEndpoint -- and every permission it names exists.
 * A new endpoint that forgets this fails the build instead of being open to any signed-in user.
 */
class EndpointAuthorizationTest {

    private static final Pattern KEY_IN_EXPRESSION = Pattern.compile("'([a-z_]+\\.[a-z_]+)'");

    @Test
    void everyEndpointIsGuardedOrMarkedPublic() throws ClassNotFoundException {
        List<String> unguarded = new ArrayList<>();
        for (Method endpoint : endpoints()) {
            if (!isGuarded(endpoint) && !isPublic(endpoint)) {
                unguarded.add(endpoint.getDeclaringClass().getSimpleName() + "." + endpoint.getName());
            }
        }
        assertThat(unguarded).as("endpoints without a permission, @SignedInUser, @VendorEndpoint or @PublicEndpoint").isEmpty();
    }

    @Test
    void everyPermissionAnEndpointNamesExists() throws ClassNotFoundException {
        List<String> unknown = new ArrayList<>();
        for (Method endpoint : endpoints()) {
            for (String key : permissionKeys(endpoint)) {
                if (Permission.fromKey(key).isEmpty()) {
                    unknown.add(endpoint.getDeclaringClass().getSimpleName() + "." + endpoint.getName() + " -> " + key);
                }
            }
        }
        assertThat(unknown).isEmpty();
    }

    @Test
    void platformEndpointsOnlyUsePlatformOrCompanyAdministrationPermissions() throws ClassNotFoundException {
        // /api/platform/companies/{id}/users etc. are shared with Company Admins (CompanyAccessGuard),
        // so they may use company permissions; but no company-side endpoint may require a platform-only one.
        List<String> leaks = new ArrayList<>();
        for (Method endpoint : endpoints()) {
            boolean platformPath = pathsOf(endpoint).stream().allMatch(path -> path.startsWith("/api/platform"));
            for (String key : permissionKeys(endpoint)) {
                boolean platformOnly = Permission.fromKey(key).map(p -> !p.companyAssignable()).orElse(false);
                if (platformOnly && !platformPath) {
                    leaks.add(endpoint.getDeclaringClass().getSimpleName() + "." + endpoint.getName() + " -> " + key);
                }
            }
        }
        assertThat(leaks).isEmpty();
    }

    private static List<Method> endpoints() throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        List<Method> endpoints = new ArrayList<>();
        for (BeanDefinition candidate : scanner.findCandidateComponents("com.wrapzzo.backend.controller")) {
            Class<?> controller = Class.forName(candidate.getBeanClassName());
            for (Method method : controller.getDeclaredMethods()) {
                if (AnnotatedElementUtils.hasAnnotation(method, RequestMapping.class)) {
                    endpoints.add(method);
                }
            }
        }
        assertThat(endpoints).as("controllers found").hasSizeGreaterThan(100);
        return endpoints;
    }

    private static boolean isGuarded(Method endpoint) {
        return AnnotatedElementUtils.hasAnnotation(endpoint, PreAuthorize.class)
                || AnnotatedElementUtils.hasAnnotation(endpoint.getDeclaringClass(), PreAuthorize.class);
    }

    private static boolean isPublic(Method endpoint) {
        return endpoint.isAnnotationPresent(PublicEndpoint.class) || endpoint.getDeclaringClass().isAnnotationPresent(PublicEndpoint.class);
    }

    /** The method's own guard if it has one (it overrides the class's), else the class's. */
    private static List<String> permissionKeys(Method endpoint) {
        List<String> keys = new ArrayList<>();
        boolean methodGuarded = AnnotatedElementUtils.hasAnnotation(endpoint, PreAuthorize.class);
        AnnotatedElement source = methodGuarded ? endpoint : endpoint.getDeclaringClass();
        RequiresPermission requires = source.getAnnotation(RequiresPermission.class);
        if (requires != null) {
            keys.add(requires.value());
        }
        PreAuthorize direct = source.getAnnotation(PreAuthorize.class);
        if (direct != null) {
            Matcher matcher = KEY_IN_EXPRESSION.matcher(direct.value());
            while (matcher.find()) {
                keys.add(matcher.group(1));
            }
        }
        return keys;
    }

    private static List<String> pathsOf(Method endpoint) {
        RequestMapping onClass = AnnotatedElementUtils.findMergedAnnotation(endpoint.getDeclaringClass(), RequestMapping.class);
        RequestMapping onMethod = AnnotatedElementUtils.findMergedAnnotation(endpoint, RequestMapping.class);
        String base = onClass != null && onClass.path().length > 0 ? onClass.path()[0] : "";
        if (onMethod == null || onMethod.path().length == 0) {
            return List.of(base);
        }
        List<String> paths = new ArrayList<>();
        for (String path : onMethod.path()) {
            paths.add(base + path);
        }
        return paths;
    }
}
