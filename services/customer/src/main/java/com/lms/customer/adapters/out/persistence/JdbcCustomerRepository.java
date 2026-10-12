package com.lms.customer.adapters.out.persistence;

import com.lms.customer.application.port.out.CustomerRepository;
import com.lms.customer.domain.Customer;
import com.lms.customer.domain.CustomerId;
import com.lms.customer.domain.CustomerStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Spring JDBC persistence for customers (no JPA/Hibernate, per CLAUDE.md). The table carries {@code
 * created_at/updated_at/version} as mandated; Phase 0 inserts once and never mutates.
 */
@Repository
public class JdbcCustomerRepository implements CustomerRepository {

    private final JdbcClient jdbc;

    public JdbcCustomerRepository(JdbcClient jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    @Override
    public void save(Customer customer) {
        jdbc.sql(
                        """
                        insert into customers
                            (id, full_name, phone, date_of_birth, status, created_at, updated_at, version)
                        values (:id, :fullName, :phone, :dob, :status, :createdAt, :createdAt, 0)
                        """)
                .param("id", customer.id().value())
                .param("fullName", customer.fullName())
                .param("phone", customer.phone())
                .param("dob", customer.dateOfBirth())
                .param("status", customer.status().name())
                .param("createdAt", java.sql.Timestamp.from(customer.createdAt()))
                .update();
    }

    @Override
    public Optional<Customer> findById(CustomerId id) {
        return jdbc.sql(
                        """
                        select id, full_name, phone, date_of_birth, status, created_at
                        from customers where id = :id
                        """)
                .param("id", id.value())
                .query(JdbcCustomerRepository::mapRow)
                .optional();
    }

    private static Customer mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new Customer(
                new CustomerId(rs.getObject("id", java.util.UUID.class)),
                rs.getString("full_name"),
                rs.getString("phone"),
                rs.getObject("date_of_birth", java.time.LocalDate.class),
                CustomerStatus.valueOf(rs.getString("status")),
                rs.getTimestamp("created_at").toInstant());
    }
}
