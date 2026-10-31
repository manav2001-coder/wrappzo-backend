package com.wrapzzo.backend.security;

import com.wrapzzo.backend.security.Permission.Keys;

/**
 * "Any of these" gates for the Access Control screens. Reading a template or a feature is what an
 * assignment screen needs too, so those reads open to anyone who can use one of them (MobixFL's
 * {@code TEMPLATE_READ_PERMISSIONS}) -- an assigner can fill their picker without being allowed to
 * browse the masters.
 */
public final class TbacAccess {

    public static final String TEMPLATE_READ = "hasAnyAuthority('" + Keys.TBAC_TEMPLATE_MASTER_VIEW + "','"
            + Keys.TBAC_FEATURES_TO_TEMPLATE_MANAGE + "','" + Keys.TBAC_TEMPLATES_TO_TEMPLATE_MANAGE + "','"
            + Keys.TBAC_USER_TEMPLATE_MANAGE + "','" + Keys.TBAC_USER_TEMPLATE_ASSIGNMENT + "','"
            + Keys.TBAC_BULK_ASSIGN_USER_TEMPLATES + "','" + Keys.TBAC_ROLE_TEMPLATE_MANAGE + "','"
            + Keys.TBAC_ROLE_TEMPLATE_ASSIGNMENT + "')";

    public static final String FEATURE_READ = "hasAnyAuthority('" + Keys.TBAC_FEATURE_MASTER_VIEW + "','"
            + Keys.TBAC_FEATURES_TO_TEMPLATE_MANAGE + "','" + Keys.TBAC_FEATURES_TO_TEMPLATE_ASSIGNMENT + "')";

    public static final String REFERENCES_READ = "hasAnyAuthority('" + Keys.TBAC_TEMPLATES_TO_TEMPLATE_MANAGE + "','"
            + Keys.TBAC_TEMPLATE_MASTER_VIEW + "')";

    public static final String USER_READ = "hasAnyAuthority('" + Keys.TBAC_USER_TEMPLATE_MANAGE + "','"
            + Keys.TBAC_BULK_ASSIGN_USER_TEMPLATES + "')";

    private TbacAccess() {
    }
}
