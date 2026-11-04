package com.wrapzzo.backend.security;

import com.wrapzzo.backend.security.Permission.Keys;

/**
 * "Any of these" gates for the Access Control screens. Reading a template or a feature is what an
 * assignment screen needs too, so those reads open to anyone who can use one of them (MobixFL's
 * {@code TEMPLATE_READ_PERMISSIONS}) -- an assigner can fill their picker without being allowed to
 * browse the masters.
 */
public final class TbacAccess {

    public static final String TEMPLATE_READ = "hasAnyAuthority('" + Keys.TEMPLATE_MASTER_VIEW + "','"
            + Keys.FEATURES_TO_TEMPLATE_VIEW + "','" + Keys.TEMPLATES_TO_TEMPLATE_VIEW + "','"
            + Keys.USER_TEMPLATE_VIEW + "','" + Keys.USER_TEMPLATE_ASSIGN + "','"
            + Keys.USER_TEMPLATE_BULK_ASSIGN + "','" + Keys.ROLE_TEMPLATE_VIEW + "','"
            + Keys.ROLE_TEMPLATE_ASSIGN + "')";

    public static final String FEATURE_READ = "hasAnyAuthority('" + Keys.FEATURE_MASTER_VIEW + "','"
            + Keys.FEATURES_TO_TEMPLATE_VIEW + "','" + Keys.FEATURES_TO_TEMPLATE_ASSIGN + "')";

    public static final String REFERENCES_READ = "hasAnyAuthority('" + Keys.TEMPLATES_TO_TEMPLATE_VIEW + "','"
            + Keys.TEMPLATE_MASTER_VIEW + "')";

    public static final String USER_READ = "hasAnyAuthority('" + Keys.USER_TEMPLATE_VIEW + "','"
            + Keys.USER_TEMPLATE_BULK_ASSIGN + "')";

    private TbacAccess() {
    }
}
