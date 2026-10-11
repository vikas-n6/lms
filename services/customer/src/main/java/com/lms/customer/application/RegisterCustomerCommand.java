package com.lms.customer.application;

import java.time.LocalDate;

/** Input to register a customer. Validation of the resulting aggregate lives in the domain. */
public record RegisterCustomerCommand(String fullName, String phone, LocalDate dateOfBirth) {}
