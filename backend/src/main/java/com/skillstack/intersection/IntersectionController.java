package com.skillstack.intersection;

import tools.jackson.databind.ObjectMapper;
import com.skillstack.graph.persistence.GraphNodeRepository;
import com.skillstack.project.api.ProjectDtos;
import com.skillstack.project.application.ProjectService;
import com.skillstack.user.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/intersections")
public class IntersectionController {
    private static final Logger log = LoggerFactory.getLogger(IntersectionController.class);
    private final IntersectionDecisionRepository decisions;
    private final GraphNodeRepository nodes;
    private final UserService users;
    private final ProjectService projects;
    private final ObjectMapper objectMapper;

    public IntersectionController(IntersectionDecisionRepository decisions, GraphNodeRepository nodes, UserService users,
                                  ProjectService projects, ObjectMapper objectMapper) {
        this.decisions = decisions;
        this.nodes = nodes;
        this.users = users;
        this.projects = projects;
        this.objectMapper = objectMapper;
    }

    public record DecisionRequest(
            @NotBlank @Size(max = 180) String title,
            @NotEmpty @Size(max = 6) List<UUID> contributingNodeIds,
            boolean rejected
    ) {}

    public record ConvertRequest(
            @NotBlank @Size(max = 180) String title,
            @Size(max = 2000) String description,
            @NotBlank @Size(max = 60) @Pattern(regexp = "engineering|ai|business|economics|ideas|culture|world|health|communication") String domain,
            @NotEmpty @Size(max = 6) List<UUID> contributingNodeIds
    ) {}

    @PutMapping("/{definitionKey}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void decide(Authentication auth, @PathVariable @Size(max = 180) String definitionKey, @Valid @RequestBody DecisionRequest request) {
        request.contributingNodeIds().forEach(id -> nodes.findByIdAndOwnerSubject(id, auth.getName())
                .orElseThrow(() -> com.skillstack.common.ApiException.notFound("node")));
        var owner = users.ensureUser(auth.getName(), auth.getName());
        var json = writeIds(request.contributingNodeIds());
        var existing = decisions.findByOwnerSubjectAndDefinitionKey(auth.getName(), definitionKey);
        if (existing.filter(item -> item.getDecision() == IntersectionDecision.Decision.CONVERTED).isPresent()) {
            throw com.skillstack.common.ApiException.conflict("INTERSECTION_ALREADY_CONVERTED", "A converted intersection cannot be changed");
        }
        var decision = existing
                .orElseGet(() -> new IntersectionDecision(owner, definitionKey, request.title(),
                        request.rejected() ? IntersectionDecision.Decision.REJECTED : IntersectionDecision.Decision.SAVED, json));
        decision.update(request.title(), request.rejected() ? IntersectionDecision.Decision.REJECTED : IntersectionDecision.Decision.SAVED, json, null);
        var saved = decisions.save(decision);
        log.info("Intersection decision persisted decisionId={} ownerId={} state={}", saved.getId(), owner.getId(), saved.getDecision());
    }

    @PostMapping("/{definitionKey}/project")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public ProjectDtos.ProjectResponse convert(Authentication auth, @PathVariable @Size(max = 180) String definitionKey,
                                                @Valid @RequestBody ConvertRequest request) {
        var existing = decisions.findByOwnerSubjectAndDefinitionKey(auth.getName(), definitionKey);
        if (existing.isPresent() && existing.get().getDecision() == IntersectionDecision.Decision.CONVERTED
                && existing.get().getResultingProject() != null) {
            return projects.get(auth.getName(), existing.get().getResultingProject().getId());
        }
        var project = projects.create(auth.getName(), new ProjectDtos.CreateProjectRequest(
                request.title(), request.description(), request.domain(), (short) 8,
                com.skillstack.project.domain.ProjectStatus.PLANNED, request.contributingNodeIds()));
        var owner = users.ensureUser(auth.getName(), auth.getName());
        var json = writeIds(request.contributingNodeIds());
        var entity = existing
                .orElseGet(() -> new IntersectionDecision(owner, definitionKey, request.title(), IntersectionDecision.Decision.CONVERTED, json));
        entity.update(request.title(), IntersectionDecision.Decision.CONVERTED, json, projects.requireProject(auth.getName(), project.id()));
        var saved = decisions.save(entity);
        log.info("Intersection converted decisionId={} projectId={} ownerId={}", saved.getId(), project.id(), owner.getId());
        return project;
    }

    private String writeIds(List<UUID> ids) {
        try { return objectMapper.writeValueAsString(ids); }
        catch (Exception exception) { throw new IllegalStateException("Unable to serialize contributing nodes", exception); }
    }
}
