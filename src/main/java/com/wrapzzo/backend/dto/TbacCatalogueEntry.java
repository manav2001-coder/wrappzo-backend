package com.wrapzzo.backend.dto;

import com.wrapzzo.backend.model.AccessControl;
import java.util.UUID;

/** A permission key the platform defines, for the Feature Master's "register features" dialog. */
public record TbacCatalogueEntry(UUID id, String key, String moduleName, String actionName) {

    public static TbacCatalogueEntry from(AccessControl feature) {
        return new TbacCatalogueEntry(feature.getId(), feature.getKey(), feature.getCategory(), feature.getName());
    }
}
