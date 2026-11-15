package com.crm_demo.exception;

import com.crm_demo.dto.ErrorResponse;
import com.crm_demo.entity.CandidateStatus;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Collections;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler exceptionHandler;

    @Mock
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        exceptionHandler = new GlobalExceptionHandler();
        when(request.getRequestURI()).thenReturn("/api/v1/candidates/test");
    }

    @Test
    @DisplayName("handleNotFound - returns 404")
    void handleNotFound() {
        ResourceNotFoundException ex = new ResourceNotFoundException("Candidate not found");
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleNotFound(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("Candidate not found");
    }

    @Test
    @DisplayName("handleDuplicate - returns 409")
    void handleDuplicate() {
        DuplicateResourceException ex = new DuplicateResourceException("Email already exists");
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleDuplicate(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("Email already exists");
    }

    @Test
    @DisplayName("handleBadRequest - returns 400")
    void handleBadRequest() {
        BadRequestException ex = new BadRequestException("Invalid parameter");
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleBadRequest(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("Invalid parameter");
    }

    @Test
    @DisplayName("handleValidationErrors - returns 400 with field errors")
    void handleValidationErrors() {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "target");
        bindingResult.addError(new FieldError("target", "firstName", "must not be blank"));

        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(null, bindingResult);

        ResponseEntity<ErrorResponse> response = exceptionHandler.handleValidationErrors(ex, request);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().fieldErrors()).containsEntry("firstName", "must not be blank");
    }

    @Test
    @DisplayName("handleConstraintViolation - with and without path details")
    void handleConstraintViolation() {
        // Empty violations
        ConstraintViolationException emptyEx = new ConstraintViolationException("Invalid data", Collections.emptySet());
        ResponseEntity<ErrorResponse> emptyResponse = exceptionHandler.handleConstraintViolation(emptyEx, request);
        assertThat(emptyResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // Violation with non-null property path
        ConstraintViolation<?> mockViolation = Mockito.mock(ConstraintViolation.class);
        Path mockPath = Mockito.mock(Path.class);
        when(mockPath.toString()).thenReturn("candidate.email");
        when(mockViolation.getPropertyPath()).thenReturn(mockPath);
        when(mockViolation.getMessage()).thenReturn("must be a well-formed email address");

        ConstraintViolationException ex = new ConstraintViolationException("Invalid data", Set.of(mockViolation));
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleConstraintViolation(ex, request);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().fieldErrors()).containsEntry("candidate.email", "must be a well-formed email address");

        // Violation with null property path
        ConstraintViolation<?> nullPathViolation = Mockito.mock(ConstraintViolation.class);
        when(nullPathViolation.getPropertyPath()).thenReturn(null);
        when(nullPathViolation.getMessage()).thenReturn("invalid parameter");
        ConstraintViolationException nullPathEx = new ConstraintViolationException("Invalid", Set.of(nullPathViolation));
        ResponseEntity<ErrorResponse> nullPathResp = exceptionHandler.handleConstraintViolation(nullPathEx, request);
        assertThat(nullPathResp.getBody().fieldErrors()).containsEntry("parameter", "invalid parameter");
    }

    @Test
    @DisplayName("handleMalformedJson - default message when cause is null")
    void handleMalformedJson_nullCause() {
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException("Malformed JSON", (org.springframework.http.HttpInputMessage) null);
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleMalformedJson(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).contains("Malformed JSON request body");
    }

    @Test
    @DisplayName("handleMalformedJson - InvalidFormatException for non-enum")
    void handleMalformedJson_invalidFormatNonEnum() {
        JsonMappingException.Reference ref = new JsonMappingException.Reference("testObj", "yearsOfExperience");
        InvalidFormatException ife = new InvalidFormatException(null, "invalid format", "abc", Integer.class);
        ife.prependPath(ref);

        HttpMessageNotReadableException ex = new HttpMessageNotReadableException("Invalid format", ife, (org.springframework.http.HttpInputMessage) null);
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleMalformedJson(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().fieldErrors()).containsKey("yearsOfExperience");
        assertThat(response.getBody().fieldErrors().get("yearsOfExperience")).contains("Expected type: Integer");
    }

    @Test
    @DisplayName("handleMalformedJson - InvalidFormatException for enum")
    void handleMalformedJson_invalidFormatEnum() {
        JsonMappingException.Reference ref = new JsonMappingException.Reference("testObj", "status");
        InvalidFormatException ife = new InvalidFormatException(null, "invalid format", "PENDING", CandidateStatus.class);
        ife.prependPath(ref);

        HttpMessageNotReadableException ex = new HttpMessageNotReadableException("Invalid format", ife, (org.springframework.http.HttpInputMessage) null);
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleMalformedJson(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().fieldErrors()).containsKey("status");
        assertThat(response.getBody().fieldErrors().get("status")).contains("Allowed values:");
    }

    @Test
    @DisplayName("handleMalformedJson - InvalidFormatException with null targetType and null fieldName")
    void handleMalformedJson_nullTargetType() {
        JsonMappingException.Reference ref = new JsonMappingException.Reference("testObj", 0);
        InvalidFormatException ife = new InvalidFormatException(null, "invalid format", "badVal", null);
        ife.prependPath(ref);

        HttpMessageNotReadableException ex = new HttpMessageNotReadableException("Invalid format", ife, (org.springframework.http.HttpInputMessage) null);
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleMalformedJson(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().fieldErrors()).containsKey("[unknown]");
    }

    @Test
    @DisplayName("handleMalformedJson - MismatchedInputException")
    void handleMalformedJson_mismatchedInput() {
        JsonMappingException.Reference ref = new JsonMappingException.Reference("testObj", "skills");
        MismatchedInputException mie = MismatchedInputException.from(null, String.class, "mismatched");
        mie.prependPath(ref);

        HttpMessageNotReadableException ex = new HttpMessageNotReadableException("Mismatched", mie, (org.springframework.http.HttpInputMessage) null);
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleMalformedJson(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().fieldErrors()).containsKey("skills");
        assertThat(response.getBody().message()).contains("Data structure mismatch");
    }

    @Test
    @DisplayName("handleMalformedJson - JsonParseException")
    void handleMalformedJson_jsonParseException() {
        JsonParseException jpe = new JsonParseException(null, "Unexpected character ':'");

        HttpMessageNotReadableException ex = new HttpMessageNotReadableException("Syntax error", jpe, (org.springframework.http.HttpInputMessage) null);
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleMalformedJson(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().message()).contains("Syntax error in JSON");
    }

    @Test
    @DisplayName("handleTypeMismatch - with required type and null required type")
    void handleTypeMismatch() {
        MethodArgumentTypeMismatchException ex =
                new MethodArgumentTypeMismatchException("abc", Long.class, "id", null, null);
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleTypeMismatch(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().message()).contains("Parameter 'id' should be of type 'Long'");

        MethodArgumentTypeMismatchException nullTypeEx =
                new MethodArgumentTypeMismatchException("abc", null, "id", null, null);
        ResponseEntity<ErrorResponse> nullTypeResp = exceptionHandler.handleTypeMismatch(nullTypeEx, request);
        assertThat(nullTypeResp.getBody().message()).contains("Parameter 'id' should be of type 'unknown'");
    }

    @Test
    @DisplayName("handleNoResourceFound - returns 404 with helpful endpoint guidance")
    void handleNoResourceFound() {
        org.springframework.web.servlet.resource.NoResourceFoundException ex =
                new org.springframework.web.servlet.resource.NoResourceFoundException(org.springframework.http.HttpMethod.GET, "/unknown-path");
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleNoResourceFound(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).contains("The requested path '/api/v1/candidates/test' does not exist");
    }

    @Test
    @DisplayName("handleMethodNotSupported - returns 405 with supported methods")
    void handleMethodNotSupported() {
        org.springframework.web.HttpRequestMethodNotSupportedException ex =
                new org.springframework.web.HttpRequestMethodNotSupportedException("POST", java.util.List.of("GET", "HEAD"));
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleMethodNotSupported(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).contains("Supported methods: [GET, HEAD]");

        // With null supported methods
        org.springframework.web.HttpRequestMethodNotSupportedException nullMethodsEx =
                new org.springframework.web.HttpRequestMethodNotSupportedException("DELETE");
        ResponseEntity<ErrorResponse> nullMethodsResp = exceptionHandler.handleMethodNotSupported(nullMethodsEx, request);
        assertThat(nullMethodsResp.getBody().message()).contains("Supported methods: none");
    }

    @Test
    @DisplayName("handleUnhandledException - returns 500")
    void handleUnhandledException() {
        Exception ex = new RuntimeException("Unexpected DB crash");
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleUnhandledException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).contains("unexpected internal error");
    }
}
