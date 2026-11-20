package com.skillstack.project.api;

import com.skillstack.project.application.ProjectService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/projects")
public class ProjectController {
    private final ProjectService projects;

    public ProjectController(ProjectService projects) { this.projects = projects; }

    @GetMapping
    public List<ProjectDtos.ProjectResponse> list(Authentication auth) { return projects.list(auth.getName()); }

    @GetMapping("/{id}")
    public ProjectDtos.ProjectResponse get(Authentication auth, @PathVariable UUID id) { return projects.get(auth.getName(), id); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectDtos.ProjectResponse create(Authentication auth, @Valid @RequestBody ProjectDtos.CreateProjectRequest request) {
        return projects.create(auth.getName(), request);
    }

    @PatchMapping("/{id}")
    public ProjectDtos.ProjectResponse update(Authentication auth, @PathVariable UUID id, @Valid @RequestBody ProjectDtos.UpdateProjectRequest request) {
        return projects.update(auth.getName(), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(Authentication auth, @PathVariable UUID id) { projects.delete(auth.getName(), id); }

    @GetMapping("/{id}/proofs")
    public List<ProjectDtos.ProofResponse> proofs(Authentication auth, @PathVariable UUID id) { return projects.proofs(auth.getName(), id); }

    @PostMapping("/{id}/proofs")
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectDtos.ProofResponse addProof(Authentication auth, @PathVariable UUID id, @Valid @RequestBody ProjectDtos.CreateProofRequest request) {
        return projects.addProof(auth.getName(), id, request);
    }

    @DeleteMapping("/proofs/{proofId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteProof(Authentication auth, @PathVariable UUID proofId) { projects.deleteProof(auth.getName(), proofId); }
}
