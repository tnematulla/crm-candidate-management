package com.crm_demo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class CandidateManagementApplicationTest {

    @Test
    @DisplayName("Application context loads successfully")
    void contextLoads() {
        // Verifies that all beans, JPA repositories, and async configurations wire correctly
        assertThat(CandidateManagementApplication.class).isNotNull();
    }
}

