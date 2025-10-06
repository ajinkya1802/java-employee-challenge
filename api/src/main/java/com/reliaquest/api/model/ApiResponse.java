package com.reliaquest.api.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * Generic class to deserialize the standard response structure from the Mock API:
 * { "data": T, "status": "...", "error": "..." }
 * @param <T> The type of the data field (e.g., Employee, List<Employee>, String, Boolean)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true) // Useful for handling unexpected fields
public class ApiResponse<T> {

    private T data;
    private String status;
    private String error;
}