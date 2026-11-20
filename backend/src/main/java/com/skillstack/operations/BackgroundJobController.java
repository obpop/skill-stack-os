package com.skillstack.operations;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/operations")
public class BackgroundJobController {
    private final BackgroundJobService jobs;

    public BackgroundJobController(BackgroundJobService jobs) { this.jobs = jobs; }

    @PostMapping("/recommendations")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public JobDtos.Job start(Authentication authentication, @Valid @RequestBody JobDtos.StartRecommendationRequest request) {
        return jobs.startRecommendation(authentication.getName(), request);
    }

    @GetMapping("/jobs/{id}")
    public JobDtos.Job get(Authentication authentication, @PathVariable UUID id) {
        return jobs.get(authentication.getName(), id);
    }

    @GetMapping("/jobs")
    public List<JobDtos.Job> recent(Authentication authentication) { return jobs.recent(authentication.getName()); }

    @PostMapping("/jobs/{id}/cancel")
    public JobDtos.Job cancel(Authentication authentication, @PathVariable UUID id) {
        return jobs.cancel(authentication.getName(), id);
    }

    @PostMapping("/jobs/{id}/retry")
    public JobDtos.Job retry(Authentication authentication, @PathVariable UUID id) {
        return jobs.retry(authentication.getName(), id);
    }

    @GetMapping("/summary")
    public JobDtos.Summary summary(Authentication authentication) { return jobs.summary(authentication.getName()); }
}
