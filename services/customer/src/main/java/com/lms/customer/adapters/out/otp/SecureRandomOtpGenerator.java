package com.lms.customer.adapters.out.otp;

import com.lms.customer.application.OtpCodeGenerator;
import java.security.SecureRandom;
import org.springframework.stereotype.Component;

/** A 6-digit numeric OTP generator backed by {@link SecureRandom}. */
@Component
public class SecureRandomOtpGenerator implements OtpCodeGenerator {

    private static final int BOUND = 1_000_000;

    private final SecureRandom random = new SecureRandom();

    @Override
    public String generate() {
        return String.format("%06d", random.nextInt(BOUND));
    }
}
