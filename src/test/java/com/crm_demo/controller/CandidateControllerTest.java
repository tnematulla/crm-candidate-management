package com.crm_demo.controller;

import com.crm_demo.dto.BulkImportResponse;
import com.crm_demo.dto.CandidateFilterCriteria;
import com.crm_demo.dto.CandidateRequest;
import com.crm_demo.dto.CandidateResponse;
import com.crm_demo.entity.CandidateStatus;
import com.crm_demo.exception.DuplicateResourceException;
import com.crm_demo.exception.GlobalExceptionHandler;
import com.crm_demo.exception.ResourceNotFoundException;
import com.crm_demo.service.CandidateService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CandidateController.class)
@Import(GlobalExceptionHandler.class)
class CandidateControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CandidateService candidateService;

    @Test
    @DisplayName("POST /api/v1/candidates - Valid request should return 201 Created")
    void createCandidate_valid_returns201() throws Exception {
        CandidateRequest request = new CandidateRequest(
                "Alex", "Morgan", "alex.m@example.com", "+1-555-4321",
                "Frontend Engineer", 4, new BigDecimal("105000.00"),
                "Boston, MA", Set.of("JavaScript", "TypeScript", "React"),
                CandidateStatus.APPLIED, "Frontend specialist"
        );

        CandidateResponse response = new CandidateResponse(
                10L, "Alex", "Morgan", "alex.m@example.com", "+1-555-4321",
                "Frontend Engineer", 4, new BigDecimal("105000.00"),
                "Boston, MA", Set.of("JavaScript", "TypeScript", "React"),
                CandidateStatus.APPLIED, "Frontend specialist",
                Instant.now(), Instant.now()
        );

        when(candidateService.createCandidate(any(CandidateRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/candidates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is(10)))
                .andExpect(jsonPath("$.email", is("alex.m@example.com")))
                .andExpect(jsonPath("$.firstName", is("Alex")));
    }

    @Test
    @DisplayName("POST /api/v1/candidates - Invalid email format should return 400 Bad Request")
    void createCandidate_invalidEmail_returns400() throws Exception {
        CandidateRequest request = new CandidateRequest(
                "Alex", "Morgan", "not-a-valid-email", "+1-555-4321",
                "Frontend Engineer", 4, new BigDecimal("105000.00"),
                "Boston, MA", Set.of("React"), CandidateStatus.APPLIED, null
        );

        mockMvc.perform(post("/api/v1/candidates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.fieldErrors.email", containsString("Invalid email format")));
    }

    @Test
    @DisplayName("POST /api/v1/candidates - Experience exceeding 80 years should return 400 Bad Request")
    void createCandidate_excessiveExperience_returns400() throws Exception {
        CandidateRequest request = new CandidateRequest(
                "Alex", "Morgan", "alex.morgan@example.com", "+1-555-4321",
                "Engineer", 150, new BigDecimal("105000.00"),
                "Boston, MA", Set.of("Java"), CandidateStatus.APPLIED, null
        );

        mockMvc.perform(post("/api/v1/candidates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.fieldErrors.yearsOfExperience", containsString("cannot exceed 80 years")));
    }

    @Test
    @DisplayName("POST /api/v1/candidates - Duplicate email conflict should return 409 Conflict")
    void createCandidate_duplicateEmail_returns409() throws Exception {
        CandidateRequest request = new CandidateRequest(
                "Alex", "Morgan", "alex.m@example.com", null, null,
                3, null, null, null, null, null
        );

        when(candidateService.createCandidate(any(CandidateRequest.class)))
                .thenThrow(new DuplicateResourceException("Candidate with email alex.m@example.com already exists"));

        mockMvc.perform(post("/api/v1/candidates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status", is(409)))
                .andExpect(jsonPath("$.message", containsString("already exists")));
    }

    @Test
    @DisplayName("GET /api/v1/candidates/{id} - Existing candidate should return 200 OK")
    void getCandidateById_existing_returns200() throws Exception {
        CandidateResponse response = new CandidateResponse(
                1L, "Alex", "Morgan", "alex.m@example.com", "+1-555-4321",
                "Frontend Engineer", 4, new BigDecimal("105000.00"),
                "Boston, MA", Set.of("React"), CandidateStatus.APPLIED, null,
                Instant.now(), Instant.now()
        );

        when(candidateService.getCandidateById(1L)).thenReturn(response);

        mockMvc.perform(get("/api/v1/candidates/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.firstName", is("Alex")));
    }

    @Test
    @DisplayName("GET /api/v1/candidates/{id} - Non-existent candidate should return 404 Not Found")
    void getCandidateById_nonExistent_returns404() throws Exception {
        when(candidateService.getCandidateById(999L))
                .thenThrow(new ResourceNotFoundException("Candidate not found with id: 999"));

        mockMvc.perform(get("/api/v1/candidates/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.message", containsString("not found")));
    }

    @Test
    @DisplayName("PUT /api/v1/candidates/{id} - Valid update should return 200 OK")
    void updateCandidate_valid_returns200() throws Exception {
        CandidateRequest request = new CandidateRequest(
                "Alex", "Morgan", "alex.updated@example.com", "+1-555-4321",
                "Lead Frontend Engineer", 5, new BigDecimal("125000.00"),
                "Boston, MA", Set.of("React", "Next.js"), CandidateStatus.INTERVIEWED, null
        );

        CandidateResponse response = new CandidateResponse(
                1L, "Alex", "Morgan", "alex.updated@example.com", "+1-555-4321",
                "Lead Frontend Engineer", 5, new BigDecimal("125000.00"),
                "Boston, MA", Set.of("React", "Next.js"), CandidateStatus.INTERVIEWED, null,
                Instant.now(), Instant.now()
        );

        when(candidateService.updateCandidate(eq(1L), any(CandidateRequest.class))).thenReturn(response);

        mockMvc.perform(put("/api/v1/candidates/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentTitle", is("Lead Frontend Engineer")))
                .andExpect(jsonPath("$.status", is("INTERVIEWED")));
    }

    @Test
    @DisplayName("DELETE /api/v1/candidates/{id} - Existing candidate returns 204 No Content")
    void deleteCandidate_existing_returns204() throws Exception {
        doNothing().when(candidateService).deleteCandidate(1L);

        mockMvc.perform(delete("/api/v1/candidates/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("DELETE /api/v1/candidates/{id} - Non-existent candidate returns 404 Not Found")
    void deleteCandidate_nonExistent_returns404() throws Exception {
        doThrow(new ResourceNotFoundException("Candidate not found with id: 999"))
                .when(candidateService).deleteCandidate(999L);

        mockMvc.perform(delete("/api/v1/candidates/999"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /api/v1/candidates - Dynamic search with filters returns 200 OK with Page")
    void searchAndFilter_returnsPage() throws Exception {
        CandidateResponse response = new CandidateResponse(
                1L, "Alex", "Morgan", "alex.m@example.com", null,
                "Java Dev", 3, null, "Seattle", Set.of("Java"),
                CandidateStatus.APPLIED, null, Instant.now(), Instant.now()
        );

        when(candidateService.searchAndFilter(any(CandidateFilterCriteria.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(response)));

        mockMvc.perform(get("/api/v1/candidates")
                        .param("keyword", "Java")
                        .param("minExperience", "2")
                        .param("status", "APPLIED")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].firstName", is("Alex")))
                .andExpect(jsonPath("$.content[0].skills", hasItem("Java")));
    }

    @Test
    @DisplayName("POST /api/v1/candidates/batch - Multi-threaded batch import returns 200 OK")
    void batchImport_returns200() throws Exception {
        CandidateRequest request = new CandidateRequest(
                "Alex", "Morgan", "alex.m@example.com", null, "Dev", 2, null, null, null, null, null
        );

        BulkImportResponse bulkResponse = new BulkImportResponse(
                1, 1, 0, 45L, "Multi-Threaded Parallel Chunk Executor", Collections.emptyList()
        );

        when(candidateService.importCandidatesInBulk(any())).thenReturn(bulkResponse);

        mockMvc.perform(post("/api/v1/candidates/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(request))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSubmitted", is(1)))
                .andExpect(jsonPath("$.totalSuccess", is(1)))
                .andExpect(jsonPath("$.processingMechanism", containsString("Multi-Threaded")));
    }

    @Test
    @DisplayName("POST /api/v1/candidates/bulk-upload-csv - Valid CSV file returns 200 OK")
    void uploadCsv_valid_returns200() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "candidates.csv", "text/csv",
                "firstName,lastName,email\nTest,User,test.csv@example.com".getBytes()
        );

        BulkImportResponse bulkResponse = new BulkImportResponse(
                1, 1, 0, 30L, "Multi-Threaded Parallel Chunk Executor", Collections.emptyList()
        );

        when(candidateService.importCandidatesFromCsv(any())).thenReturn(bulkResponse);

        mockMvc.perform(multipart("/api/v1/candidates/bulk-upload-csv").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSuccess", is(1)));
    }

    @Test
    @DisplayName("POST /api/v1/candidates/bulk-upload-csv - Empty file returns 400 Bad Request")
    void uploadCsv_empty_returns400() throws Exception {
        MockMultipartFile emptyFile = new MockMultipartFile("file", "empty.csv", "text/csv", new byte[0]);

        mockMvc.perform(multipart("/api/v1/candidates/bulk-upload-csv").file(emptyFile))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("must not be empty")));
    }

    @Test
    @DisplayName("GET /api/v1/candidates - Ascending sort parameter parsing")
    void searchAndFilter_ascendingSort() throws Exception {
        when(candidateService.searchAndFilter(any(), any()))
                .thenReturn(new PageImpl<>(Collections.emptyList()));

        mockMvc.perform(get("/api/v1/candidates")
                        .param("sort", "lastName,asc"))
                .andExpect(status().isOk());
    }
}
