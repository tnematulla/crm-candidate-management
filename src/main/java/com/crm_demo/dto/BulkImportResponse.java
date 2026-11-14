package com.crm_demo.dto;

import java.util.List;

public record BulkImportResponse(
        int totalSubmitted,
        int totalSuccess,
        int totalFailed,
        long executionTimeMs,
        String processingMechanism,
        List<String> errorMessages
) {
}
