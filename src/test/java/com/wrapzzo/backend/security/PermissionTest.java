package com.wrapzzo.backend.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class PermissionTest {

    @Test
    void everyKeyIsModuleDotAction() {
        for (Permission permission : Permission.values()) {
            assertThat(permission.key()).matches("[a-z_]+\\.[a-z_]+");
            assertThat(permission.key()).isEqualTo(permission.module() + "." + permission.action());
        }
    }

    @Test
    void theKeysConstantsAndTheEnumListTheSamePermissions() throws IllegalAccessException {
        Set<String> constants = new HashSet<>();
        for (Field field : Permission.Keys.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) && field.getType() == String.class) {
                constants.add((String) field.get(null));
            }
        }
        Set<String> enumKeys = Arrays.stream(Permission.values()).map(Permission::key).collect(Collectors.toSet());
        assertThat(constants).isEqualTo(enumKeys);
        for (Permission permission : Permission.values()) {
            assertThat(Permission.Keys.class.getDeclaredFields()).anyMatch(f -> f.getName().equals(permission.name()));
        }
    }

    @Test
    void anyActionImpliesItsModulesView() {
        assertThat(Permission.CAMPAIGN_EDIT.impliedView()).contains(Permission.CAMPAIGN_VIEW);
        assertThat(Permission.ORDER_ARTWORK_FINAL.impliedView()).contains(Permission.ORDER_VIEW);
        assertThat(Permission.CAMPAIGN_VIEW.impliedView()).isEmpty();
        // A module without a view action implies nothing.
        assertThat(Permission.REQUEST_APPROVE.impliedView()).isEmpty();
    }

    @Test
    void platformModulesAreNeverCompanyAssignable() {
        for (Permission permission : Permission.values()) {
            assertThat(permission.companyAssignable()).as(permission.key()).isEqualTo(!permission.module().startsWith("platform_"));
        }
    }
}
