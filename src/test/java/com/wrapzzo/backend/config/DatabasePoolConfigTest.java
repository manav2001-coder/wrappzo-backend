package com.wrapzzo.backend.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** The pool must be sized for real traffic, not HikariCP's default of 10. */
@SpringBootTest
class DatabasePoolConfigTest {

    @Autowired
    private DataSource dataSource;

    @Test
    void usesAPoolSizedForRealTraffic() throws Exception {
        HikariDataSource pool = dataSource.unwrap(HikariDataSource.class);
        assertThat(pool.getPoolName()).isEqualTo("wrapzzo-db");
        assertThat(pool.getMaximumPoolSize()).isEqualTo(30);
        assertThat(pool.getMinimumIdle()).isEqualTo(10);
        assertThat(pool.getConnectionTimeout()).isEqualTo(10_000);
        assertThat(pool.getMaxLifetime()).isEqualTo(1_500_000);
        assertThat(pool.getLeakDetectionThreshold()).isEqualTo(30_000);
    }
}
