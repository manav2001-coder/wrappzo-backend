package com.wrapzzo.backend.security;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The permission catalogue: every grantable action, as {@code module.action}. This enum is the
 * single source of truth in code -- endpoints reference {@link Keys} (compile-time constants, so
 * they work inside annotations), services reference the enum. The access_controls table holds
 * the same keys (seeded by migrations, with their labels) because grants are foreign keys to it;
 * PermissionCatalogVerifier fails startup if the two ever drift apart.
 *
 * <p>Company-assignable permissions can be granted to a company's roles, designations and users;
 * the rest are platform-only (Wrappzo staff, across every company). Platform super admins hold
 * every permission implicitly (see AccessControlCacheService).
 */
public enum Permission {

    DASHBOARD_VIEW(Keys.DASHBOARD_VIEW, true),

    CAMPAIGN_VIEW(Keys.CAMPAIGN_VIEW, true),
    CAMPAIGN_CREATE(Keys.CAMPAIGN_CREATE, true),
    CAMPAIGN_EDIT(Keys.CAMPAIGN_EDIT, true),
    CAMPAIGN_PUBLISH(Keys.CAMPAIGN_PUBLISH, true),

    CAMPAIGN_ENTRY_SUBMIT(Keys.CAMPAIGN_ENTRY_SUBMIT, true),
    CAMPAIGN_ENTRY_VIEW(Keys.CAMPAIGN_ENTRY_VIEW, true),

    REQUEST_CREATE(Keys.REQUEST_CREATE, true),
    REQUEST_APPROVE(Keys.REQUEST_APPROVE, true),

    PRODUCT_VIEW(Keys.PRODUCT_VIEW, true),

    ORDER_VIEW(Keys.ORDER_VIEW, true),
    ORDER_ARTWORK_REVIEW(Keys.ORDER_ARTWORK_REVIEW, true),
    ORDER_ARTWORK_FINAL(Keys.ORDER_ARTWORK_FINAL, true),

    RECIPIENT_VIEW(Keys.RECIPIENT_VIEW, true),
    RECIPIENT_CREATE(Keys.RECIPIENT_CREATE, true),
    RECIPIENT_EDIT(Keys.RECIPIENT_EDIT, true),

    RECIPIENT_LIST_MANAGE(Keys.RECIPIENT_LIST_MANAGE, true),
    RECIPIENT_LIST_APPROVE(Keys.RECIPIENT_LIST_APPROVE, true),

    USER_VIEW(Keys.USER_VIEW, true),
    USER_CREATE(Keys.USER_CREATE, true),
    USER_EDIT(Keys.USER_EDIT, true),
    USER_DELETE(Keys.USER_DELETE, true),

    ROLE_VIEW(Keys.ROLE_VIEW, true),
    ROLE_EDIT(Keys.ROLE_EDIT, true),

    TBAC_FEATURE_MASTER_VIEW(Keys.TBAC_FEATURE_MASTER_VIEW, true),
    TBAC_FEATURE_MASTER_ADD(Keys.TBAC_FEATURE_MASTER_ADD, true),
    TBAC_FEATURE_MASTER_EDIT(Keys.TBAC_FEATURE_MASTER_EDIT, true),
    TBAC_TEMPLATE_MASTER_VIEW(Keys.TBAC_TEMPLATE_MASTER_VIEW, true),
    TBAC_TEMPLATE_MASTER_ADD(Keys.TBAC_TEMPLATE_MASTER_ADD, true),
    TBAC_TEMPLATE_MASTER_EDIT(Keys.TBAC_TEMPLATE_MASTER_EDIT, true),
    TBAC_FEATURES_TO_TEMPLATE_MANAGE(Keys.TBAC_FEATURES_TO_TEMPLATE_MANAGE, true),
    TBAC_FEATURES_TO_TEMPLATE_ASSIGNMENT(Keys.TBAC_FEATURES_TO_TEMPLATE_ASSIGNMENT, true),
    TBAC_TEMPLATES_TO_TEMPLATE_MANAGE(Keys.TBAC_TEMPLATES_TO_TEMPLATE_MANAGE, true),
    TBAC_TEMPLATES_TO_TEMPLATE_ASSIGNMENT(Keys.TBAC_TEMPLATES_TO_TEMPLATE_ASSIGNMENT, true),
    TBAC_USER_TEMPLATE_MANAGE(Keys.TBAC_USER_TEMPLATE_MANAGE, true),
    TBAC_USER_TEMPLATE_ASSIGNMENT(Keys.TBAC_USER_TEMPLATE_ASSIGNMENT, true),
    TBAC_BULK_ASSIGN_USER_TEMPLATES(Keys.TBAC_BULK_ASSIGN_USER_TEMPLATES, true),
    TBAC_ROLE_TEMPLATE_MANAGE(Keys.TBAC_ROLE_TEMPLATE_MANAGE, true),
    TBAC_ROLE_TEMPLATE_ASSIGNMENT(Keys.TBAC_ROLE_TEMPLATE_ASSIGNMENT, true),

    COMPANY_VENDOR_VIEW(Keys.COMPANY_VENDOR_VIEW, true),
    COMPANY_VENDOR_ADD(Keys.COMPANY_VENDOR_ADD, true),

    WAREHOUSE_VIEW(Keys.WAREHOUSE_VIEW, true),
    WAREHOUSE_SETUP(Keys.WAREHOUSE_SETUP, true),
    WAREHOUSE_OPERATE(Keys.WAREHOUSE_OPERATE, true),

    MENU_EDIT(Keys.MENU_EDIT, true),

    PLATFORM_COMPANY_VIEW(Keys.PLATFORM_COMPANY_VIEW, false),
    PLATFORM_COMPANY_CREATE(Keys.PLATFORM_COMPANY_CREATE, false),
    PLATFORM_COMPANY_EDIT(Keys.PLATFORM_COMPANY_EDIT, false),
    PLATFORM_COMPANY_STATUS(Keys.PLATFORM_COMPANY_STATUS, false),
    PLATFORM_COMPANY_CONFIGURE(Keys.PLATFORM_COMPANY_CONFIGURE, false),

    PLATFORM_VENDOR_VIEW(Keys.PLATFORM_VENDOR_VIEW, false),
    PLATFORM_VENDOR_CREATE(Keys.PLATFORM_VENDOR_CREATE, false),
    PLATFORM_VENDOR_EDIT(Keys.PLATFORM_VENDOR_EDIT, false),
    PLATFORM_VENDOR_STATUS(Keys.PLATFORM_VENDOR_STATUS, false),

    PLATFORM_PRODUCT_VIEW(Keys.PLATFORM_PRODUCT_VIEW, false),
    PLATFORM_PRODUCT_CREATE(Keys.PLATFORM_PRODUCT_CREATE, false),
    PLATFORM_PRODUCT_EDIT(Keys.PLATFORM_PRODUCT_EDIT, false),
    PLATFORM_PRODUCT_STATUS(Keys.PLATFORM_PRODUCT_STATUS, false),

    PLATFORM_ORDER_VIEW(Keys.PLATFORM_ORDER_VIEW, false),
    PLATFORM_ORDER_MANAGE(Keys.PLATFORM_ORDER_MANAGE, false),

    PLATFORM_DASHBOARD_VIEW(Keys.PLATFORM_DASHBOARD_VIEW, false);

    /** The action every module that can be read at all is read with; granting any other action of a module implies it. */
    public static final String VIEW_ACTION = "view";

    private static final Map<String, Permission> BY_KEY =
            Arrays.stream(values()).collect(Collectors.toUnmodifiableMap(Permission::key, Function.identity()));

    private final String key;
    private final String module;
    private final String action;
    private final boolean companyAssignable;

    Permission(String key, boolean companyAssignable) {
        int dot = key.indexOf('.');
        this.key = key;
        this.module = key.substring(0, dot);
        this.action = key.substring(dot + 1);
        this.companyAssignable = companyAssignable;
    }

    public String key() {
        return key;
    }

    public String module() {
        return module;
    }

    public String action() {
        return action;
    }

    public boolean companyAssignable() {
        return companyAssignable;
    }

    public static Optional<Permission> fromKey(String key) {
        return Optional.ofNullable(BY_KEY.get(key));
    }

    public static List<Permission> companyAssignablePermissions() {
        return Arrays.stream(values()).filter(Permission::companyAssignable).toList();
    }

    /** The module's view permission, if it has one -- what granting any of its other actions also grants. */
    public Optional<Permission> impliedView() {
        return VIEW_ACTION.equals(action) ? Optional.empty() : fromKey(module + "." + VIEW_ACTION);
    }

    /** Compile-time constants for annotations ({@code @RequiresPermission(Keys.ORDER_VIEW)}). */
    public static final class Keys {

        public static final String DASHBOARD_VIEW = "dashboard.view";

        public static final String CAMPAIGN_VIEW = "campaign.view";
        public static final String CAMPAIGN_CREATE = "campaign.create";
        public static final String CAMPAIGN_EDIT = "campaign.edit";
        public static final String CAMPAIGN_PUBLISH = "campaign.publish";

        public static final String CAMPAIGN_ENTRY_SUBMIT = "campaign_entry.submit";
        public static final String CAMPAIGN_ENTRY_VIEW = "campaign_entry.view";

        public static final String REQUEST_CREATE = "request.create";
        public static final String REQUEST_APPROVE = "request.approve";

        public static final String PRODUCT_VIEW = "product.view";

        public static final String ORDER_VIEW = "order.view";
        public static final String ORDER_ARTWORK_REVIEW = "order.artwork_review";
        public static final String ORDER_ARTWORK_FINAL = "order.artwork_final";

        public static final String RECIPIENT_VIEW = "recipient.view";
        public static final String RECIPIENT_CREATE = "recipient.create";
        public static final String RECIPIENT_EDIT = "recipient.edit";

        public static final String RECIPIENT_LIST_MANAGE = "recipient_list.manage";
        public static final String RECIPIENT_LIST_APPROVE = "recipient_list.approve";

        public static final String USER_VIEW = "user.view";
        public static final String USER_CREATE = "user.create";
        public static final String USER_EDIT = "user.edit";
        public static final String USER_DELETE = "user.delete";

        public static final String ROLE_VIEW = "role.view";
        public static final String ROLE_EDIT = "role.edit";

        public static final String TBAC_FEATURE_MASTER_VIEW = "tbac.feature_master.view";
        public static final String TBAC_FEATURE_MASTER_ADD = "tbac.feature_master.add";
        public static final String TBAC_FEATURE_MASTER_EDIT = "tbac.feature_master.edit";
        public static final String TBAC_TEMPLATE_MASTER_VIEW = "tbac.template_master.view";
        public static final String TBAC_TEMPLATE_MASTER_ADD = "tbac.template_master.add";
        public static final String TBAC_TEMPLATE_MASTER_EDIT = "tbac.template_master.edit";
        public static final String TBAC_FEATURES_TO_TEMPLATE_MANAGE = "tbac.features_to_template.manage";
        public static final String TBAC_FEATURES_TO_TEMPLATE_ASSIGNMENT = "tbac.features_to_template.assignment";
        public static final String TBAC_TEMPLATES_TO_TEMPLATE_MANAGE = "tbac.templates_to_template.manage";
        public static final String TBAC_TEMPLATES_TO_TEMPLATE_ASSIGNMENT = "tbac.templates_to_template.assignment";
        public static final String TBAC_USER_TEMPLATE_MANAGE = "tbac.user_template.manage";
        public static final String TBAC_USER_TEMPLATE_ASSIGNMENT = "tbac.user_template.assignment";
        public static final String TBAC_BULK_ASSIGN_USER_TEMPLATES = "tbac.bulk_assign_user_templates";
        public static final String TBAC_ROLE_TEMPLATE_MANAGE = "tbac.role_template.manage";
        public static final String TBAC_ROLE_TEMPLATE_ASSIGNMENT = "tbac.role_template.assignment";

        public static final String COMPANY_VENDOR_VIEW = "company_vendor.view";
        public static final String COMPANY_VENDOR_ADD = "company_vendor.add";

        public static final String WAREHOUSE_VIEW = "warehouse.view";
        public static final String WAREHOUSE_SETUP = "warehouse.setup";
        public static final String WAREHOUSE_OPERATE = "warehouse.operate";

        public static final String MENU_EDIT = "menu.edit";

        public static final String PLATFORM_COMPANY_VIEW = "platform_company.view";
        public static final String PLATFORM_COMPANY_CREATE = "platform_company.create";
        public static final String PLATFORM_COMPANY_EDIT = "platform_company.edit";
        public static final String PLATFORM_COMPANY_STATUS = "platform_company.status";
        public static final String PLATFORM_COMPANY_CONFIGURE = "platform_company.configure";

        public static final String PLATFORM_VENDOR_VIEW = "platform_vendor.view";
        public static final String PLATFORM_VENDOR_CREATE = "platform_vendor.create";
        public static final String PLATFORM_VENDOR_EDIT = "platform_vendor.edit";
        public static final String PLATFORM_VENDOR_STATUS = "platform_vendor.status";

        public static final String PLATFORM_PRODUCT_VIEW = "platform_product.view";
        public static final String PLATFORM_PRODUCT_CREATE = "platform_product.create";
        public static final String PLATFORM_PRODUCT_EDIT = "platform_product.edit";
        public static final String PLATFORM_PRODUCT_STATUS = "platform_product.status";

        public static final String PLATFORM_ORDER_VIEW = "platform_order.view";
        public static final String PLATFORM_ORDER_MANAGE = "platform_order.manage";

        public static final String PLATFORM_DASHBOARD_VIEW = "platform_dashboard.view";

        private Keys() {
        }
    }
}
