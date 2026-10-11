package com.lms.customer.adapters.in.web;

import com.lms.customer.application.GetCustomerService;
import com.lms.customer.application.RegisterCustomerCommand;
import com.lms.customer.application.RegisterCustomerService;
import com.lms.customer.domain.Customer;
import com.lms.customer.domain.CustomerId;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** REST adapter for customers. Routed to from the gateway at {@code /api/customers/**}. */
@RestController
@RequestMapping("/customers")
public class CustomerController {

    private final RegisterCustomerService registerCustomer;
    private final GetCustomerService getCustomer;

    public CustomerController(
            RegisterCustomerService registerCustomer, GetCustomerService getCustomer) {
        this.registerCustomer = registerCustomer;
        this.getCustomer = getCustomer;
    }

    @PostMapping
    public ResponseEntity<CustomerResponse> register(
            @Valid @RequestBody RegisterCustomerRequest request) {
        Customer customer =
                registerCustomer.register(
                        new RegisterCustomerCommand(
                                request.fullName(), request.phone(), request.dateOfBirth()));
        CustomerResponse body = CustomerResponse.from(customer);
        return ResponseEntity.created(URI.create("/customers/" + body.id())).body(body);
    }

    @GetMapping("/{id}")
    public CustomerResponse get(@PathVariable String id) {
        return CustomerResponse.from(getCustomer.byId(CustomerId.of(id)));
    }
}
