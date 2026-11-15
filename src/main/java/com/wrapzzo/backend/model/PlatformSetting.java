package com.wrapzzo.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** One platform-wide setting a Super Admin can change from the app: a key and its value, and who last changed it. */
@Entity
@Table(name = "platform_settings")
@Getter
@NoArgsConstructor
public class PlatformSetting {

    @Id
    @Column(name = "key", length = 100)
    private String key;

    @Column(nullable = false, length = 500)
    private String value;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by_name", length = 200)
    private String updatedByName;

    public PlatformSetting(String key, String value, String updatedByName) {
        this.key = key;
        change(value, updatedByName);
    }

    public void change(String value, String updatedByName) {
        this.value = value;
        this.updatedByName = updatedByName;
        this.updatedAt = Instant.now();
    }
}
