package com.meridian.payments;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Meridian Payments - Failed Scheduled Report Recovery Service.
 * Scans execution logs, recovers safe retriable report jobs, and alerts on failures.
 */
@SpringBootApplication
@EnableScheduling
@EnableTransactionManagement
public class MeridianPaymentsApplication {

    public static void main(String[] args) {
        SpringApplication.run(MeridianPaymentsApplication.class, args);
    }
}
