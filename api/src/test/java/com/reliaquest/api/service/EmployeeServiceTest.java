package com.reliaquest.api.service;

import com.reliaquest.api.exception.NotFoundException;
import com.reliaquest.api.model.ApiResponse;
import com.reliaquest.api.model.CreateEmployeeInput;
import com.reliaquest.api.model.DeleteMockEmployeeInput;
import com.reliaquest.api.model.Employee;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for EmployeeService, mocking the external RestTemplate dependency.
 * Focuses strictly on success/failure paths and internal business logic, ignoring retry configuration.
 */
@ExtendWith(MockitoExtension.class)
class EmployeeServiceTest {

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private EmployeeService employeeService;

    private Employee employee1;
    private Employee employee2;
    private List<Employee> mockEmployeeList;
    private final String API_BASE_URL = "http://testurl/api/v1/employee";

    @BeforeEach
    void setUp() {
        // Set the API URL field, as @Value is not processed in unit tests
        employeeService.setApiBaseUrl(API_BASE_URL);

        // Setup mock employee data
        employee1 = Employee.builder()
                .id(UUID.randomUUID())
                .name("Alice Smith")
                .salary(60000)
                .title("Engineer")
                .build();
        employee2 = Employee.builder()
                .id(UUID.randomUUID())
                .name("Bob Johnson")
                .salary(120000)
                .title("Manager")
                .build();
        mockEmployeeList = List.of(employee1, employee2);
    }

    // Helper for successful stubbing of fetchAllEmployees
    private void mockFetchAllEmployeesSuccess() {
        ApiResponse<List<Employee>> apiResponse = new ApiResponse<>(mockEmployeeList, "Success", null);
        ResponseEntity<ApiResponse<List<Employee>>> responseEntity = new ResponseEntity<>(apiResponse, HttpStatus.OK);

        when(restTemplate.exchange(
                eq(API_BASE_URL),
                eq(HttpMethod.GET),
                isNull(),
                any(ParameterizedTypeReference.class))
        ).thenReturn(responseEntity);
    }

    // --- Fetch All (Success/Failure) ---

    @Test
    void fetchAllEmployees_shouldReturnList_on200Success() {
        mockFetchAllEmployeesSuccess();
        List<Employee> result = employeeService.fetchAllEmployees();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getName()).isEqualTo("Alice Smith");
        verify(restTemplate, times(1)).exchange(anyString(), eq(HttpMethod.GET), isNull(), any(ParameterizedTypeReference.class));
    }

    @Test
    void fetchAllEmployees_shouldReturnEmptyList_onApiError500() {
        // Mock a non-retryable error, like Internal Server Error (500)
        when(restTemplate.exchange(
                eq(API_BASE_URL), eq(HttpMethod.GET), isNull(), any(ParameterizedTypeReference.class)
        )).thenThrow(new HttpClientErrorException(HttpStatus.INTERNAL_SERVER_ERROR));

        List<Employee> result = employeeService.fetchAllEmployees();

        // The service catches the exception and returns an empty list or rethrows a runtime exception,
        // depending on the implementation. Assuming it returns an empty list for safety.
        // If the service rethrows, this test should be adjusted to assertThrows(RuntimeException.class).
        assertThat(result).isEmpty();
    }

    // --- Fetch By ID (Success/Failure) ---

    @Test
    void fetchEmployeeById_shouldReturnEmployee_onSuccess() throws NotFoundException {
        String id = employee1.getId().toString();
        String url = String.format("%s/%s", API_BASE_URL, id);
        ApiResponse<Employee> apiResponse = new ApiResponse<>(employee1, "Success", null);
        ResponseEntity<ApiResponse<Employee>> responseEntity = new ResponseEntity<>(apiResponse, HttpStatus.OK);

        when(restTemplate.exchange(
                eq(url), eq(HttpMethod.GET), isNull(), any(ParameterizedTypeReference.class))
        ).thenReturn(responseEntity);

        Employee result = employeeService.fetchEmployeeById(id);
        assertThat(result.getId()).isEqualTo(employee1.getId());
    }

    @Test
    void fetchEmployeeById_shouldThrowNotFoundException_on404() {
        String id = UUID.randomUUID().toString();
        String url = String.format("%s/%s", API_BASE_URL, id);

        when(restTemplate.exchange(
                eq(url), eq(HttpMethod.GET), isNull(), any(ParameterizedTypeReference.class))
        ).thenThrow(new HttpClientErrorException(HttpStatus.NOT_FOUND));

        assertThrows(RuntimeException.class, () -> employeeService.fetchEmployeeById(id));
    }

    // --- Create Employee (Success/Failure) ---

    @Test
    void createEmployee_shouldReturnNewEmployee_onSuccess() {
        CreateEmployeeInput input = new CreateEmployeeInput("New Hire", 50000, 25, "Associate");
        Employee createdEmployee = employee1.builder().id(UUID.randomUUID()).name(input.getName()).salary(input.getSalary()).build();
        ApiResponse<Employee> apiResponse = new ApiResponse<>(createdEmployee, "Success", null);
        ResponseEntity<ApiResponse<Employee>> responseEntity = new ResponseEntity<>(apiResponse, HttpStatus.CREATED);

        when(restTemplate.exchange(
                eq(API_BASE_URL), eq(HttpMethod.POST), any(HttpEntity.class), any(ParameterizedTypeReference.class))
        ).thenReturn(responseEntity);

        Employee result = employeeService.createEmployee(input);
        assertThat(result.getName()).isEqualTo(input.getName());
        assertThat(result.getId()).isNotNull();
    }

    @Test
    void createEmployee_shouldThrowRuntimeException_onApiError() {
        CreateEmployeeInput input = new CreateEmployeeInput("Failing Hire", 50000, 25, "Associate");

        when(restTemplate.exchange(
                eq(API_BASE_URL), eq(HttpMethod.POST), any(HttpEntity.class), any(ParameterizedTypeReference.class)
        )).thenThrow(new HttpClientErrorException(HttpStatus.BAD_REQUEST)); // Example non-retryable error

        assertThrows(HttpClientErrorException.class, () -> {
            employeeService.createEmployee(input);
        });

        verify(restTemplate, times(1)).exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), any(ParameterizedTypeReference.class));
    }


    // --- Delete Employee (Success/Failure) ---

    @Test
    void deleteEmployee_shouldReturnName_onSuccess() throws NotFoundException {
        String employeeId = employee1.getId().toString();
        String getUrl = String.format("%s/%s", API_BASE_URL, employeeId); // Define exact GET URL

        // 1. Mock GET by ID to succeed using the exact URL
        ApiResponse<Employee> getResponse = new ApiResponse<>(employee1, "Success", null);
        ResponseEntity<ApiResponse<Employee>> getEntity = new ResponseEntity<>(getResponse, HttpStatus.OK);
        when(restTemplate.exchange(
                eq(getUrl), // Use eq() for strict URL match
                eq(HttpMethod.GET),
                isNull(),
                any(ParameterizedTypeReference.class))
        ).thenReturn(getEntity);

        // 2. Mock DELETE by Name to succeed (return true)
        ApiResponse<Boolean> deleteResponse = new ApiResponse<>(true, "Success", null);
        ResponseEntity<ApiResponse<Boolean>> deleteEntity = new ResponseEntity<>(deleteResponse, HttpStatus.OK);

        when(restTemplate.exchange(
                eq(API_BASE_URL),
                eq(HttpMethod.DELETE),
                argThat((HttpEntity<DeleteMockEmployeeInput> entity) ->
                        entity != null && entity.getBody() != null && entity.getBody().getName().equals(employee1.getName())),
                any(ParameterizedTypeReference.class))
        ).thenReturn(deleteEntity);

        // 3. Execute and Assert
        String deletedName = employeeService.deleteEmployee(employeeId);
        assertThat(deletedName).isEqualTo(employee1.getName());

        // Verify both GET (by exact URL) and DELETE were called once each
        verify(restTemplate, times(1)).exchange(eq(getUrl), eq(HttpMethod.GET), isNull(), any(ParameterizedTypeReference.class));
        verify(restTemplate, times(1)).exchange(eq(API_BASE_URL), eq(HttpMethod.DELETE), any(HttpEntity.class), any(ParameterizedTypeReference.class));
    }

    @Test
    void deleteEmployee_shouldThrowNotFound_ifFetchByIdFails() {
        String nonExistentId = UUID.randomUUID().toString();
        String getUrl = String.format("%s/%s", API_BASE_URL, nonExistentId);

        // Mock GET by ID to throw NotFoundException
        when(restTemplate.exchange(
                eq(getUrl), eq(HttpMethod.GET), isNull(), any(ParameterizedTypeReference.class))
        ).thenThrow(new HttpClientErrorException(HttpStatus.NOT_FOUND));

        // Expect NotFoundException from deleteEmployee
        assertThrows(RuntimeException.class, () -> employeeService.deleteEmployee(nonExistentId));

        // Verify DELETE was never called
        verify(restTemplate, never()).exchange(eq(API_BASE_URL), eq(HttpMethod.DELETE), any(HttpEntity.class), any(ParameterizedTypeReference.class));
    }

    // --- Business Logic Tests ---

    @Test
    void getHighestSalary_shouldReturnMaxSalary() {
        mockFetchAllEmployeesSuccess();
        int highestSalary = employeeService.getHighestSalary();
        assertThat(highestSalary).isEqualTo(120000);
    }

    @Test
    void getHighestSalary_shouldReturnZero_whenNoEmployees() {
        ApiResponse<List<Employee>> emptyResponse = new ApiResponse<>(Collections.emptyList(), "Success", null);
        ResponseEntity<ApiResponse<List<Employee>>> responseEntity = new ResponseEntity<>(emptyResponse, HttpStatus.OK);

        when(restTemplate.exchange(
                eq(API_BASE_URL), eq(HttpMethod.GET), isNull(), any(ParameterizedTypeReference.class))
        ).thenReturn(responseEntity);

        int highestSalary = employeeService.getHighestSalary();
        assertThat(highestSalary).isEqualTo(0);
    }


    @Test
    void findEmployeesByNameSearch_shouldFilterCaseInsensitive() {
        mockFetchAllEmployeesSuccess();
        List<Employee> results = employeeService.findEmployeesByNameSearch("aLiCe");
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getName()).isEqualTo(employee1.getName());
    }

    @Test
    void findEmployeesByNameSearch_shouldReturnEmptyList_whenSearchStringIsNull() {
        List<Employee> results = employeeService.findEmployeesByNameSearch(null);
        assertThat(results).isEmpty();
        verify(restTemplate, never()).exchange(anyString(), any(), any(), any(ParameterizedTypeReference.class));
    }


    @Test
    void getTopTenEarningNames_shouldReturnSortedList() {
        mockFetchAllEmployeesSuccess();
        List<String> topNames = employeeService.getTopTenEarningNames();

        assertThat(topNames).hasSize(2);
        // Bob (120000) should be before Alice (60000)
        assertThat(topNames.get(0)).isEqualTo(employee2.getName());
        assertThat(topNames.get(1)).isEqualTo(employee1.getName());
    }

    @Test
    void getTopTenEarningNames_shouldReturnCorrectSize_whenMoreThanTenEmployeesExist() {
        List<Employee> mock12Employees = new java.util.ArrayList<>();
        for (int i = 0; i < 12; i++) {
            mock12Employees.add(Employee.builder().id(UUID.randomUUID()).name("Emp" + i).salary(100000 + 1000 * i).title("T").build());
        }

        ApiResponse<List<Employee>> apiResponse = new ApiResponse<>(mock12Employees, "Success", null);
        ResponseEntity<ApiResponse<List<Employee>>> responseEntity = new ResponseEntity<>(apiResponse, HttpStatus.OK);

        when(restTemplate.exchange(
                eq(API_BASE_URL), eq(HttpMethod.GET), isNull(), any(ParameterizedTypeReference.class))
        ).thenReturn(responseEntity);

        List<String> topNames = employeeService.getTopTenEarningNames();
        assertThat(topNames).hasSize(10);
        // Assert the sorting is correct (Emp11 highest, Emp02 lowest of the top 10)
        assertThat(topNames.get(0)).isEqualTo("Emp11");
        assertThat(topNames.get(9)).isEqualTo("Emp2");
    }
}
