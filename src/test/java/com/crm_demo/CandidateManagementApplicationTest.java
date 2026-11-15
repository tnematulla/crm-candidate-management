package com.crm_demo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

@SpringBootTest
class CandidateManagementApplicationTest {

    @Test
    @DisplayName("Application context loads successfully")
    void contextLoads() {
        // Verifies that all beans, JPA repositories, and async configurations wire correctly
        assertThat(CandidateManagementApplication.class).isNotNull();
    }

    @Test
    @DisplayName("Application main method and default constructor execute")
    void mainAndConstructorExecute() {
        try (MockedStatic<SpringApplication> mockedSpringApp = mockStatic(SpringApplication.class)) {
            String[] args = new String[]{"--server.port=0"};
            CandidateManagementApplication.main(args);
            mockedSpringApp.verify(() -> SpringApplication.run(CandidateManagementApplication.class, args));
        }

        CandidateManagementApplication app = new CandidateManagementApplication();
        assertThat(app).isNotNull();
    }
}

