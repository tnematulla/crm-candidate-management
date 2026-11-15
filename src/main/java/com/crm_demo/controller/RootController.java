package com.crm_demo.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@Tag(name = "Root", description = "Root discovery and service navigation entry point")
public class RootController {

    @GetMapping("/")
    @Operation(summary = "API Root Discovery", description = "Returns service metadata, operational status, and navigation links")
    public ResponseEntity<Map<String, Object>> getRootInfo() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("service", "Candidate Management System API");
        info.put("status", "UP");
        info.put("version", "1.0.0");
        info.put("description", "RESTful backend service for candidate management, dynamic multi-criteria search, and concurrent batch processing.");

        Map<String, String> links = new LinkedHashMap<>();
        links.put("swaggerUi", "/swagger-ui.html");
        links.put("openApiDocs", "/v3/api-docs");
        links.put("candidatesApi", "/api/v1/candidates");
        links.put("h2Console", "/h2-console");
        info.put("links", links);

        return ResponseEntity.ok(info);
    }
}
