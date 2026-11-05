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

    QUICK_GIFT_CREATE(Keys.QUICK_GIFT_CREATE, true),
    QUICK_GIFT_APPROVE(Keys.QUICK_GIFT_APPROVE, true),

    GIFT_STORE_VIEW(Keys.GIFT_STORE_VIEW, true),

    ORDER_VIEW(Keys.ORDER_VIEW, true),
    ORDER_REQUEST_PHOTO(Keys.ORDER_REQUEST_PHOTO, true),
    ARTWORK_REVIEW(Keys.ARTWORK_REVIEW, true),
    ARTWORK_FINAL_APPROVE(Keys.ARTWORK_FINAL_APPROVE, true),
    ARTWORK_APPROVAL_VIEW(Keys.ARTWORK_APPROVAL_VIEW, true),
    ARTWORK_APPROVAL_CONFIGURE(Keys.ARTWORK_APPROVAL_CONFIGURE, true),

    DOCTOR_VIEW(Keys.DOCTOR_VIEW, true),
    DOCTOR_CREATE(Keys.DOCTOR_CREATE, true),
    DOCTOR_EDIT(Keys.DOCTOR_EDIT, true),

    DOCTOR_REMOVE(Keys.DOCTOR_REMOVE, true),
    DOCTOR_IMPORT(Keys.DOCTOR_IMPORT, true),
    DOCTOR_APPROVE(Keys.DOCTOR_APPROVE, true),

    USER_VIEW(Keys.USER_VIEW, true),
    USER_CREATE(Keys.USER_CREATE, true),
    USER_EDIT(Keys.USER_EDIT, true),
    USER_DELETE(Keys.USER_DELETE, true),

    ROLE_VIEW(Keys.ROLE_VIEW, true),
    ROLE_EDIT(Keys.ROLE_EDIT, true),

    FEATURE_MASTER_VIEW(Keys.FEATURE_MASTER_VIEW, true),
    FEATURE_MASTER_ADD(Keys.FEATURE_MASTER_ADD, true),
    FEATURE_MASTER_EDIT(Keys.FEATURE_MASTER_EDIT, true),
    TEMPLATE_MASTER_VIEW(Keys.TEMPLATE_MASTER_VIEW, true),
    TEMPLATE_MASTER_ADD(Keys.TEMPLATE_MASTER_ADD, true),
    TEMPLATE_MASTER_EDIT(Keys.TEMPLATE_MASTER_EDIT, true),
    FEATURES_TO_TEMPLATE_VIEW(Keys.FEATURES_TO_TEMPLATE_VIEW, true),
    FEATURES_TO_TEMPLATE_ASSIGN(Keys.FEATURES_TO_TEMPLATE_ASSIGN, true),
    TEMPLATES_TO_TEMPLATE_VIEW(Keys.TEMPLATES_TO_TEMPLATE_VIEW, true),
    TEMPLATES_TO_TEMPLATE_ASSIGN(Keys.TEMPLATES_TO_TEMPLATE_ASSIGN, true),
    USER_TEMPLATE_VIEW(Keys.USER_TEMPLATE_VIEW, true),
    USER_TEMPLATE_ASSIGN(Keys.USER_TEMPLATE_ASSIGN, true),
    USER_TEMPLATE_BULK_ASSIGN(Keys.USER_TEMPLATE_BULK_ASSIGN, true),
    ROLE_TEMPLATE_VIEW(Keys.ROLE_TEMPLATE_VIEW, true),
    ROLE_TEMPLATE_ASSIGN(Keys.ROLE_TEMPLATE_ASSIGN, true),

    COMPANY_VENDOR_VIEW(Keys.COMPANY_VENDOR_VIEW, true),
    COMPANY_VENDOR_ADD(Keys.COMPANY_VENDOR_ADD, true),
    COMPANY_VENDOR_ASSIGN(Keys.COMPANY_VENDOR_ASSIGN, true),
    COMPANY_VENDOR_EDIT(Keys.COMPANY_VENDOR_EDIT, true),
    COMPANY_VENDOR_STATUS(Keys.COMPANY_VENDOR_STATUS, true),

    WAREHOUSE_VIEW(Keys.WAREHOUSE_VIEW, true),
    WAREHOUSE_SETUP(Keys.WAREHOUSE_SETUP, true),
    WAREHOUSE_OPERATE(Keys.WAREHOUSE_OPERATE, true),

    PLATFORM_COMPANY_VIEW(Keys.PLATFORM_COMPANY_VIEW, false),
    PLATFORM_COMPANY_CREATE(Keys.PLATFORM_COMPANY_CREATE, false),
    PLATFORM_COMPANY_EDIT(Keys.PLATFORM_COMPANY_EDIT, false),
    PLATFORM_COMPANY_STATUS(Keys.PLATFORM_COMPANY_STATUS, false),
    PLATFORM_COMPANY_CONFIGURE(Keys.PLATFORM_COMPANY_CONFIGURE, false),

    PLATFORM_VENDOR_VIEW(Keys.PLATFORM_VENDOR_VIEW, false),

    PLATFORM_GIFT_VIEW(Keys.PLATFORM_GIFT_VIEW, false),
    PLATFORM_GIFT_CREATE(Keys.PLATFORM_GIFT_CREATE, false),
    PLATFORM_GIFT_EDIT(Keys.PLATFORM_GIFT_EDIT, false),
    PLATFORM_GIFT_STATUS(Keys.PLATFORM_GIFT_STATUS, false),

    PLATFORM_ORDER_VIEW(Keys.PLATFORM_ORDER_VIEW, false),
    PLATFORM_ORDER_MANAGE(Keys.PLATFORM_ORDER_MANAGE, false),

    PLATFORM_DASHBOARD_VIEW(Keys.PLATFORM_DASHBOARD_VIEW, false),

    PLATFORM_JOBS_VIEW(Keys.PLATFORM_JOBS_VIEW, false),
    PLATFORM_JOBS_MANAGE(Keys.PLATFORM_JOBS_MANAGE, false),

    PLATFORM_MENU_CONFIG_VIEW(Keys.PLATFORM_MENU_CONFIG_VIEW, false),
    PLATFORM_MENU_CONFIG_EDIT(Keys.PLATFORM_MENU_CONFIG_EDIT, false);

    /** The action every module that can be read at all is read with; granting any other action of a module implies it. */
    public static final String VIEW_ACTION = "view";

    /** Modules whose actions are done on another module's records: artwork is reviewed on an order, so it implies seeing orders. */
    private static final Map<String, String> VIEW_OF_MODULE = Map.of("artwork", ORDER_VIEW.key);

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
        return VIEW_ACTION.equals(action) ? Optional.empty() : fromKey(VIEW_OF_MODULE.getOrDefault(module, module + "." + VIEW_ACTION));
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

        public static final String QUICK_GIFT_CREATE = "quick_gift.create";
        public static final String QUICK_GIFT_APPROVE = "quick_gift.approve";

        public static final String GIFT_STORE_VIEW = "gift_store.view";

        public static final String ORDER_VIEW = "order.view";
        public static final String ORDER_REQUEST_PHOTO = "order.request_photo";
        public static final String ARTWORK_REVIEW = "artwork.review";
        public static final String ARTWORK_FINAL_APPROVE = "artwork.final_approve";
        public static final String ARTWORK_APPROVAL_VIEW = "artwork_approval.view";
        public static final String ARTWORK_APPROVAL_CONFIGURE = "artwork_approval.configure";

        public static final String DOCTOR_VIEW = "doctor.view";
        public static final String DOCTOR_CREATE = "doctor.create";
        public static final String DOCTOR_EDIT = "doctor.edit";

        public static final String DOCTOR_REMOVE = "doctor.remove";
        public static final String DOCTOR_IMPORT = "doctor.import";
        public static final String DOCTOR_APPROVE = "doctor.approve";

        public static final String USER_VIEW = "user.view";
        public static final String USER_CREATE = "user.create";
        public static final String USER_EDIT = "user.edit";
        public static final String USER_DELETE = "user.delete";

        public static final String ROLE_VIEW = "role.view";
        public static final String ROLE_EDIT = "role.edit";

        public static final String FEATURE_MASTER_VIEW = "feature_master.view";
        public static final String FEATURE_MASTER_ADD = "feature_master.add";
        public static final String FEATURE_MASTER_EDIT = "feature_master.edit";
        public static final String TEMPLATE_MASTER_VIEW = "template_master.view";
        public static final String TEMPLATE_MASTER_ADD = "template_master.add";
        public static final String TEMPLATE_MASTER_EDIT = "template_master.edit";
        public static final String FEATURES_TO_TEMPLATE_VIEW = "features_to_template.view";
        public static final String FEATURES_TO_TEMPLATE_ASSIGN = "features_to_template.assign";
        public static final String TEMPLATES_TO_TEMPLATE_VIEW = "templates_to_template.view";
        public static final String TEMPLATES_TO_TEMPLATE_ASSIGN = "templates_to_template.assign";
        public static final String USER_TEMPLATE_VIEW = "user_template.view";
        public static final String USER_TEMPLATE_ASSIGN = "user_template.assign";
        public static final String USER_TEMPLATE_BULK_ASSIGN = "user_template.bulk_assign";
        public static final String ROLE_TEMPLATE_VIEW = "role_template.view";
        public static final String ROLE_TEMPLATE_ASSIGN = "role_template.assign";

        public static final String COMPANY_VENDOR_VIEW = "company_vendor.view";
        public static final String COMPANY_VENDOR_ADD = "company_vendor.add";
        public static final String COMPANY_VENDOR_ASSIGN = "company_vendor.assign";
        public static final String COMPANY_VENDOR_EDIT = "company_vendor.edit";
        public static final String COMPANY_VENDOR_STATUS = "company_vendor.status";

        public static final String WAREHOUSE_VIEW = "warehouse.view";
        public static final String WAREHOUSE_SETUP = "warehouse.setup";
        public static final String WAREHOUSE_OPERATE = "warehouse.operate";

        public static final String PLATFORM_COMPANY_VIEW = "platform_company.view";
        public static final String PLATFORM_COMPANY_CREATE = "platform_company.create";
        public static final String PLATFORM_COMPANY_EDIT = "platform_company.edit";
        public static final String PLATFORM_COMPANY_STATUS = "platform_company.status";
        public static final String PLATFORM_COMPANY_CONFIGURE = "platform_company.configure";

        public static final String PLATFORM_VENDOR_VIEW = "platform_vendor.view";

        public static final String PLATFORM_GIFT_VIEW = "platform_gift.view";
        public static final String PLATFORM_GIFT_CREATE = "platform_gift.create";
        public static final String PLATFORM_GIFT_EDIT = "platform_gift.edit";
        public static final String PLATFORM_GIFT_STATUS = "platform_gift.status";

        public static final String PLATFORM_ORDER_VIEW = "platform_order.view";
        public static final String PLATFORM_ORDER_MANAGE = "platform_order.manage";

        public static final String PLATFORM_DASHBOARD_VIEW = "platform_dashboard.view";

        public static final String PLATFORM_JOBS_VIEW = "platform_jobs.view";
        public static final String PLATFORM_JOBS_MANAGE = "platform_jobs.manage";

        public static final String PLATFORM_MENU_CONFIG_VIEW = "platform_menu_config.view";
        public static final String PLATFORM_MENU_CONFIG_EDIT = "platform_menu_config.edit";

        private Keys() {
        }
    }
}
