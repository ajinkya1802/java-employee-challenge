package com.reliaquest.api.service;

import com.reliaquest.api.exception.NotFoundException;
import com.reliaquest.api.model.ApiResponse;
import com.reliaquest.api.model.CreateEmployeeInput;
import com.reliaquest.api.model.DeleteMockEmployeeInput;
import com.reliaquest.api.model.Employee;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmployeeService {

    private final RestTemplate restTemplate;

    @Setter
    @Value("${mock.api.url:http://localhost:8112/api/v1/employee}")
    private String apiBaseUrl;

    // Type references for RestTemplate
    private static final ParameterizedTypeReference<ApiResponse<List<Employee>>> LIST_EMPLOYEE_TYPE = new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<ApiResponse<Employee>> SINGLE_EMPLOYEE_TYPE = new ParameterizedTypeReference<>() {};

    private static final int MAX_ATTEMPTS = 6;
    private static final int INITIAL_DELAY_MS = 4000; // 4 seconds
    /**
     * Executes the API call.
     * IMPORTANT: It catches HTTP 429 and rethrows it as ResourceAccessException
     * so that the surrounding @Retryable method can trigger a retry.
     */
    private <T> ResponseEntity<ApiResponse<T>> executeApiCall(String url, HttpMethod method, HttpEntity<?> requestEntity, ParameterizedTypeReference<ApiResponse<T>> responseType) {
        try {
            return restTemplate.exchange(url, method, requestEntity, responseType);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                log.warn("Rate limit (429) encountered on {} {}. Retrying...", method, url);
                // CORRECTED LINE: Using the single-argument constructor to avoid the strict type check on the cause.
                throw new ResourceAccessException("Rate limit exceeded: " + e.getMessage());
            }
            // Re-throw 404s, 400s, etc.
            throw e;
        }
    }

    // --- Configuration for Spring Retry ---
    // Retry on ResourceAccessException (which includes 429 errors from executeApiCall)
    private static final String RETRY_CONFIG = "retryFor = {ResourceAccessException.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2)";

    //--------------------------------------------------------------------------------------------------
    // Core API Implementations
    //--------------------------------------------------------------------------------------------------

    @Retryable(
        retryFor = {ResourceAccessException.class},
        maxAttempts = MAX_ATTEMPTS,
        backoff = @Backoff(delay = INITIAL_DELAY_MS, multiplier = 2)
    )
    public List<Employee> fetchAllEmployees() {
        log.debug("Attempting to fetch all employees from mock server.");
        try {
            ResponseEntity<ApiResponse<List<Employee>>> responseEntity = executeApiCall(
                    apiBaseUrl, HttpMethod.GET, null, LIST_EMPLOYEE_TYPE);

            if (responseEntity.getStatusCode().is2xxSuccessful() && responseEntity.getBody() != null) {
                return Objects.requireNonNullElse(responseEntity.getBody().getData(), Collections.emptyList());
            }
            log.warn("Failed to fetch employees, status code: {}", responseEntity.getStatusCode());
            return Collections.emptyList();

        } catch (ResourceAccessException e) {
            log.error("API call failed to fetch all employees after all retries.", e);
            throw new RuntimeException("Failed to fetch employees due to persistent rate limiting.", e);
        } catch (Exception e) {
            log.error("Error fetching all employees.", e);
            return Collections.emptyList();
        }
    }

    @Retryable(
            retryFor = {ResourceAccessException.class},
            maxAttempts = MAX_ATTEMPTS,
            backoff = @Backoff(delay = INITIAL_DELAY_MS, multiplier = 2)
    )
    public Employee fetchEmployeeById(String id) throws NotFoundException {
        String url = String.format("%s/%s", apiBaseUrl, id);
        log.debug("Fetching employee by ID: {}", id);

        try {
            ResponseEntity<ApiResponse<Employee>> responseEntity = executeApiCall(
                    url, HttpMethod.GET, null, SINGLE_EMPLOYEE_TYPE);

            if (responseEntity.getBody() != null && responseEntity.getBody().getData() != null) {
                return responseEntity.getBody().getData();
            }
            throw new RuntimeException("API response data was null or empty.");

        } catch (HttpClientErrorException.NotFound e) {
            throw new NotFoundException("Employee not found for ID: " + id);
        } catch (ResourceAccessException e) {
            throw new RuntimeException("Failed to retrieve employee from API after retries.", e);
        } catch (Exception e) {
            log.error("Error retrieving employee {}.", id, e);
            throw new RuntimeException("Failed to retrieve employee from API.", e);
        }
    }

    @Retryable(
            retryFor = {ResourceAccessException.class},
            maxAttempts = MAX_ATTEMPTS,
            backoff = @Backoff(delay = INITIAL_DELAY_MS, multiplier = 2)
    )
    public Employee createEmployee(CreateEmployeeInput input) {
        log.info("Attempting to create employee: {}", input.getName());
        HttpEntity<CreateEmployeeInput> request = new HttpEntity<>(input);

        try {
            ResponseEntity<ApiResponse<Employee>> responseEntity = executeApiCall(
                    apiBaseUrl,
                    HttpMethod.POST,
                    request,
                    SINGLE_EMPLOYEE_TYPE
            );

            if (responseEntity.getStatusCode().is2xxSuccessful() && responseEntity.getBody() != null) {
                return responseEntity.getBody().getData();
            }
            throw new RuntimeException("Failed to create employee. Status: " + responseEntity.getStatusCode());

        } catch (ResourceAccessException e) {
            throw new RuntimeException("Failed to create employee after retries.", e);
        }
    }

    @Retryable(
            retryFor = {ResourceAccessException.class},
            maxAttempts = MAX_ATTEMPTS,
            backoff = @Backoff(delay = INITIAL_DELAY_MS, multiplier = 2)
    )
    public String deleteEmployee(String id) throws NotFoundException {
        // 1. Find employee name (this call is now also resilient)
        Employee employeeToDelete = fetchEmployeeById(id);
        String employeeName = employeeToDelete.getName();

        log.info("Deleting employee: {} (ID: {})", employeeName, id);

        try {
            // 2. Prepare and execute DELETE by Name
            DeleteMockEmployeeInput deleteInput = new DeleteMockEmployeeInput();
            deleteInput.setName(employeeName);
            HttpEntity<DeleteMockEmployeeInput> request = new HttpEntity<>(deleteInput);

            ResponseEntity<ApiResponse<Boolean>> responseEntity = executeApiCall(
                    apiBaseUrl,
                    HttpMethod.DELETE,
                    request,
                    new ParameterizedTypeReference<>() {}
            );

            if (responseEntity.getStatusCode().is2xxSuccessful() &&
                    responseEntity.getBody() != null &&
                    Boolean.TRUE.equals(responseEntity.getBody().getData())) {

                return employeeName;
            }

            throw new RuntimeException("Delete operation failed on the server.");

        } catch (ResourceAccessException e) {
            throw new RuntimeException("Failed to delete employee after retries.", e);
        } catch (Exception e) {
            log.error("API call failed to delete employee {}.", id, e);
            throw new RuntimeException("Failed to delete employee.", e);
        }
    }

    //--------------------------------------------------------------------------------------------------
    // Business Logic / Calculation Implementations
    //--------------------------------------------------------------------------------------------------

    public List<Employee> findEmployeesByNameSearch(String searchString) {
        if (searchString == null || searchString.isBlank()) {
            return Collections.emptyList();
        }
        String lowerCaseSearch = searchString.toLowerCase();

        // This relies on the resilient fetchAllEmployees()
        return fetchAllEmployees().stream()
                .filter(e -> e.getName() != null && e.getName().toLowerCase().contains(lowerCaseSearch))
                .collect(Collectors.toList());
    }

    public int getHighestSalary() {
        // This relies on the resilient fetchAllEmployees()
        return fetchAllEmployees().stream()
                .mapToInt(Employee::getSalary)
                .max()
                .orElse(0);
    }

    public List<String> getTopTenEarningNames() {
        // This relies on the resilient fetchAllEmployees()
        return fetchAllEmployees().stream()
                .sorted(Comparator.comparing(Employee::getSalary, Comparator.reverseOrder()))
                .limit(10)
                .map(Employee::getName)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

}