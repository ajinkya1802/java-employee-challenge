package com.reliaquest.api.controller;

import com.reliaquest.api.exception.NotFoundException;
import com.reliaquest.api.model.Employee;
import com.reliaquest.api.model.CreateEmployeeInput;
import com.reliaquest.api.service.EmployeeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
public class EmployeeControllerImpl implements IEmployeeController<Employee, CreateEmployeeInput> {

    private final EmployeeService employeeService;

    @Override
    public ResponseEntity<List<Employee>> getAllEmployees() {
        log.info("Attempting to retrieve all employees.");
        try {
            List<Employee> employees = employeeService.fetchAllEmployees();
            return ResponseEntity.ok(employees);
        } catch (Exception e) {
            log.error("Error retrieving all employees: {}", e.getMessage(), e);
            // Returning a 500 status code with an empty list on failure
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(List.of());
        }
    }

    @Override
    public ResponseEntity<List<Employee>> getEmployeesByNameSearch(String searchString) {
        log.info("Searching for employees with name fragment: {}", searchString);
        try {
            // Business logic/filtering is handled in the service layer, likely by fetching all and filtering locally
            // or by implementing a dedicated endpoint if the Mock API supported it (it doesn't appear to).
            List<Employee> employees = employeeService.findEmployeesByNameSearch(searchString);
            return ResponseEntity.ok(employees);
        } catch (Exception e) {
            log.error("Error during name search: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(List.of());
        }
    }

    @Override
    public ResponseEntity<Employee> getEmployeeById(String id) {
        log.info("Attempting to retrieve employee with ID: {}", id);
        try {
            // The service handles both API call and potential 404
            Employee employee = employeeService.fetchEmployeeById(id);
            return ResponseEntity.ok(employee);
        } catch (NotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        } catch (Exception e) {
            log.error("Error retrieving employee {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Override
    public ResponseEntity<Integer> getHighestSalaryOfEmployees() {
        log.info("Calculating highest employee salary.");
        try {
            // Business logic (find max salary) is handled in the service layer
            int highestSalary = employeeService.getHighestSalary();
            return ResponseEntity.ok(highestSalary);
        } catch (Exception e) {
            log.error("Error calculating highest salary: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(0);
        }
    }

    @Override
    public ResponseEntity<List<String>> getTopTenHighestEarningEmployeeNames() {
        log.info("Retrieving top 10 highest earning employee names.");
        try {
            // Business logic (fetch all, sort, limit, extract name) is handled in the service layer
            List<String> topNames = employeeService.getTopTenEarningNames();
            return ResponseEntity.ok(topNames);
        } catch (Exception e) {
            log.error("Error retrieving top 10 earners: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(List.of());
        }
    }

    @Override
    public ResponseEntity<Employee> createEmployee(CreateEmployeeInput employeeInput) {
        log.info("Attempting to create a new employee: {}", employeeInput.getName());
        try {
            // Direct POST API call is handled in the service layer
            Employee newEmployee = employeeService.createEmployee(employeeInput);
            return ResponseEntity.status(HttpStatus.CREATED).body(newEmployee);
        } catch (Exception e) {
            log.error("Error creating employee: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Override
    public ResponseEntity<String> deleteEmployeeById(String id) {
        log.info("Attempting to delete employee with ID: {}", id);
        try {
            // The service layer handles the complex delete logic:
            // 1. GET employee by ID to get the name (since the Mock API delete endpoint uses the name in the body).
            // 2. DELETE using the name.
            String deletedEmployeeName = employeeService.deleteEmployee(id);
            return ResponseEntity.ok(deletedEmployeeName);
        } catch (NotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Employee not found for deletion.");
        } catch (Exception e) {
            log.error("Error deleting employee {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to delete employee.");
        }
    }
}