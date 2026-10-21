package com.pavti.dataobject.report;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record ReportRequest(@NotNull(message = "Choose a start date.") LocalDate fromDate,
                            @NotNull(message = "Choose an end date.") LocalDate toDate) {}
