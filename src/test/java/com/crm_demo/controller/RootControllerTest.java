package com.crm_demo.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RootController.class)
class RootControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("GET / - Root endpoint returns 200 OK with service metadata and links")
    void root_returns200WithServiceInfo() throws Exception {
        mockMvc.perform(get("/").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.service", is("Candidate Management System API")))
                .andExpect(jsonPath("$.status", is("UP")))
                .andExpect(jsonPath("$.version", is("1.0.0")))
                .andExpect(jsonPath("$.links.swaggerUi", is("/swagger-ui.html")))
                .andExpect(jsonPath("$.links.openApiDocs", is("/v3/api-docs")))
                .andExpect(jsonPath("$.links.candidatesApi", is("/api/v1/candidates")))
                .andExpect(jsonPath("$.links.h2Console", is("/h2-console")));
    }
}
