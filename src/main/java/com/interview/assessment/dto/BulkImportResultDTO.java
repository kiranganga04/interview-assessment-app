package com.interview.assessment.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Interview Management: per-request summary for a Bulk Import upload. Deliberately never
 * throws for a bad row -- the whole point of "import the good rows, report the bad ones" is
 * that a typo in row 4 doesn't block rows 1-3 and 5-40 from being created, so every row
 * either lands in createdSlotCodes or errors, and totalRows should always equal the sum of
 * createdCount + errorCount.
 */
@Data
public class BulkImportResultDTO {
    private int totalRows;
    private int createdCount;
    private int errorCount;
    private List<String> createdSlotCodes = new ArrayList<>();
    private List<RowError> errors = new ArrayList<>();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RowError {
        private int row;
        private String message;
    }
}
