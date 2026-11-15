package com.wrapzzo.backend.repository;

import com.wrapzzo.backend.model.PlatformSetting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlatformSettingRepository extends JpaRepository<PlatformSetting, String> {
}
