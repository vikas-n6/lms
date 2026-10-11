package com.lms.customer.adapters.in.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import java.time.LocalDate;

/** Request body for {@code POST /customers}. */
public record RegisterCustomerRequest(
        @NotBlank String fullName, @NotBlank String phone, @Past LocalDate dateOfBirth) {}
