package com.meridian.payments.controller;

import com.meridian.payments.dto.AlertDTO;
import com.meridian.payments.dto.FailedJobDTO;
import com.meridian.payments.dto.RetryResultDTO;
import com.meridian.payments.dto.UnresolvedJobDTO;
import com.meridian.payments.service.AlertService;
import com.meridian.payments.service.FailureDetectionService;
import com.meridian.payments.service.RetryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class JobRecoveryController {

    private final FailureDetectionService failureDetectionService;
    private final RetryService retryService;
    private final AlertService alertService;

    /**
     * GET /api/jobs/failed
     * Returns scheduled report jobs without successful completion where the latest run failed.
     */
    @GetMapping("/jobs/failed")
    public ResponseEntity<List<FailedJobDTO>> getFailedJobs() {
        log.info("REST request to fetch failed report jobs");
        List<FailedJobDTO> failedJobs = failureDetectionService.detectFailedJobs();
        return ResponseEntity.ok(failedJobs);
    }

    /**
     * POST /api/jobs/{id}/retry
     * Triggers a manual retry for the specified report schedule ID.
     */
    @PostMapping("/jobs/{id}/retry")
    public ResponseEntity<RetryResultDTO> retryJob(@PathVariable("id") Long id) {
        log.info("REST request to manually retry job for scheduleId={}", id);
        RetryResultDTO result = retryService.retryJob(id);
        return ResponseEntity.ok(result);
    }

    /**
     * GET /api/jobs/unresolved
     * Returns jobs requiring operational triage (non-retriable, max retries reached, or unknown).
     */
    @GetMapping("/jobs/unresolved")
    public ResponseEntity<List<UnresolvedJobDTO>> getUnresolvedJobs() {
        log.info("REST request to fetch unresolved report jobs");
        List<UnresolvedJobDTO> unresolved = failureDetectionService.detectUnresolvedJobs();
        return ResponseEntity.ok(unresolved);
    }

    /**
     * GET /api/alerts
     * Returns all historical and active system alerts.
     */
    @GetMapping("/alerts")
    public ResponseEntity<List<AlertDTO>> getAlerts() {
        log.info("REST request to fetch all alerts");
        List<AlertDTO> alerts = alertService.getAllAlerts();
        return ResponseEntity.ok(alerts);
    }
}
