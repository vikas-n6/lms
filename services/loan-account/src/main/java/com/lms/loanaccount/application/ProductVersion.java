package com.lms.loanaccount.application;

import com.lms.schedule.Product;
import java.time.Instant;
import java.time.LocalDate;

/** A stored product version: its parameters, the date it takes effect and when it was created. */
public record ProductVersion(Product product, LocalDate effectiveFrom, Instant createdAt) {}
