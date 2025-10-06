package com.reliaquest.api;


import com.reliaquest.api.exception.NotFoundException;
import com.reliaquest.api.model.CreateEmployeeInput;
import com.reliaquest.api.model.Employee;
import com.reliaquest.api.service.EmployeeService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration Test that hits the live Mock Employee API (http://localhost:8112).
 * This verifies the Spring Retry configuration handles rate limiting during the rapid @BeforeAll setup.
 */
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@ActiveProfiles("integration-test")
class EmployeeServiceIntegrationTest {

    @Autowired
    private EmployeeService employeeService;

    private static Employee highEarner1;
    private static Employee highEarner2;
    private static Employee targetEmployee;
    private static Employee searchTarget;
    private static final String NON_EXISTENT_ID = UUID.randomUUID().toString();

    /**
     * Setup known data on the live server. The resilience of the 'createEmployee'
     * method (due to @Retryable) is tested implicitly here, as it must succeed
     * despite the rapid execution.
     */
    @BeforeAll
    static void setupTestData(@Autowired EmployeeService service) {
        System.out.println("Starting resilient test data setup on live server...");

        // High Earner 1 (Highest Salary)
        CreateEmployeeInput input1 = new CreateEmployeeInput("Zack High Salary", 320000, 45, "Executive");
        highEarner1 = service.createEmployee(input1);

        // High Earner 2 (Second Highest Salary)
        CreateEmployeeInput input2 = new CreateEmployeeInput("Yara Mid Salary", 250000, 38, "Director");
        highEarner2 = service.createEmployee(input2);

        // Target for ID and Deletion Test
        CreateEmployeeInput input3 = new CreateEmployeeInput("Delete Me Target", 80000, 29, "Engineer");
        targetEmployee = service.createEmployee(input3);

        // Target for Name Search Test
        CreateEmployeeInput input4 = new CreateEmployeeInput("Search Fragment", 70000, 33, "Specialist");
        searchTarget = service.createEmployee(input4);

        // Assert the crucial IDs were created successfully
        assertThat(highEarner1.getId()).isNotNull();
        assertThat(targetEmployee.getId()).isNotNull();

        System.out.println("Setup complete.");
    }

    // --- Core API Retrieval Tests ---

    @Test
    @Order(1)
    void fetchEmployeeById_shouldReturnEmployee_whenExists() throws NotFoundException {
        Employee result = employeeService.fetchEmployeeById(targetEmployee.getId().toString());

        assertThat(result.getName()).isEqualTo(targetEmployee.getName());
        assertThat(result.getSalary()).isEqualTo(targetEmployee.getSalary());
    }

    @Test
    @Order(2)
    void fetchEmployeeById_shouldThrowNotFoundException_whenNotExists() {
        assertThrows(NotFoundException.class, () -> {
            employeeService.fetchEmployeeById(NON_EXISTENT_ID);
        });
    }

    @Test
    @Order(3)
    void fetchAllEmployees_shouldContainCreatedData() {
        List<Employee> allEmployees = employeeService.fetchAllEmployees();

        assertThat(allEmployees).anyMatch(e -> e.getId().equals(highEarner1.getId()));
    }

    // --- Business Logic Tests ---

    @Test
    @Order(4)
    void getHighestSalary_shouldReturnTheMaxSalary() {
        int maxSalary = employeeService.getHighestSalary();

        assertThat(maxSalary).isGreaterThanOrEqualTo(320000);
    }

    @Test
    @Order(5)
    void getTopTenEarningNames_shouldIncludeHighEarnersInOrder() {
        List<String> topNames = employeeService.getTopTenEarningNames();

        assertThat(topNames).contains(highEarner1.getName(), highEarner2.getName());
        assertThat(topNames.indexOf(highEarner1.getName())).isLessThan(topNames.indexOf(highEarner2.getName()));
    }

    @Test
    @Order(6)
    void findEmployeesByNameSearch_shouldReturnMatchingEmployee() {
        List<Employee> results = employeeService.findEmployeesByNameSearch("Fragment");

        assertThat(results).hasSizeGreaterThan(0);
        assertTrue(results.contains(searchTarget));
    }

    // --- Create and Delete Tests ---

    @Test
    @Order(7)
    void createEmployee_shouldReturnNewEmployeeWithId() {
        // This creation test runs independently, relying on the retry mechanism if needed.
        CreateEmployeeInput input = new CreateEmployeeInput("New Test Subject", 95000, 30, "Analyst");
        Employee newEmployee = employeeService.createEmployee(input);

        assertThat(newEmployee.getId()).isNotNull();
        assertThat(newEmployee.getName()).isEqualTo("New Test Subject");
    }

    @Test
    @Order(8)
    void deleteEmployee_shouldDeleteEmployeeAndReturnName() throws NotFoundException {
        // This relies on the resilient GET and DELETE calls
        String deletedName = employeeService.deleteEmployee(targetEmployee.getId().toString());

        assertThat(deletedName).isEqualTo(targetEmployee.getName());
    }
}