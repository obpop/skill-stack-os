package com.skillstack.project.application;

import com.skillstack.common.ApiException;
import com.skillstack.graph.domain.*;
import com.skillstack.graph.persistence.GraphEdgeRepository;
import com.skillstack.graph.persistence.GraphNodeRepository;
import com.skillstack.project.api.ProjectDtos;
import com.skillstack.project.domain.*;
import com.skillstack.project.persistence.ProjectRepository;
import com.skillstack.project.persistence.ProofOfWorkRepository;
import com.skillstack.publication.PublicationService;
import com.skillstack.user.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ProjectService {
    private static final Logger log = LoggerFactory.getLogger(ProjectService.class);
    private static final Map<ProjectStatus, EnumSet<ProjectStatus>> ALLOWED_TRANSITIONS = Map.of(
            ProjectStatus.IDEA, EnumSet.of(ProjectStatus.PLANNED, ProjectStatus.ARCHIVED),
            ProjectStatus.PLANNED, EnumSet.of(ProjectStatus.IDEA, ProjectStatus.BUILDING, ProjectStatus.ARCHIVED),
            ProjectStatus.BUILDING, EnumSet.of(ProjectStatus.PLANNED, ProjectStatus.COMPLETED, ProjectStatus.ARCHIVED),
            ProjectStatus.COMPLETED, EnumSet.of(ProjectStatus.BUILDING, ProjectStatus.ARCHIVED),
            ProjectStatus.ARCHIVED, EnumSet.of(ProjectStatus.PLANNED));
    private final ProjectRepository projects;
    private final ProofOfWorkRepository proofs;
    private final GraphNodeRepository nodes;
    private final GraphEdgeRepository edges;
    private final UserService users;
    private final PublicationService publication;

    public ProjectService(ProjectRepository projects, ProofOfWorkRepository proofs, GraphNodeRepository nodes,
                          GraphEdgeRepository edges, UserService users, PublicationService publication) {
        this.projects = projects;
        this.proofs = proofs;
        this.nodes = nodes;
        this.edges = edges;
        this.users = users;
        this.publication = publication;
    }

    @Transactional
    public ProjectDtos.ProjectResponse create(String subject, ProjectDtos.CreateProjectRequest request) {
        return create(subject, request, Provenance.USER_CREATED);
    }

    @Transactional
    public ProjectDtos.ProjectResponse createImported(String subject, String title) {
        return create(subject, new ProjectDtos.CreateProjectRequest(title, "Imported after your review", "ideas",
                (short) 5, ProjectStatus.IDEA, List.of()), Provenance.IMPORTED);
    }

    private ProjectDtos.ProjectResponse create(String subject, ProjectDtos.CreateProjectRequest request, Provenance provenance) {
        var owner = users.ensureUser(subject, subject);
        var concepts = request.conceptNodeIds() == null ? List.<GraphNode>of() : request.conceptNodeIds().stream()
                .distinct().map(id -> requireNode(subject, id)).toList();
        var x = concepts.stream().map(GraphNode::getLayoutX).filter(java.util.Objects::nonNull).mapToDouble(Double::doubleValue).average().orElse(4);
        var y = concepts.stream().map(GraphNode::getLayoutY).filter(java.util.Objects::nonNull).mapToDouble(Double::doubleValue).average().orElse(2);
        var graphNode = nodes.save(new GraphNode(owner, null, NodeType.PROJECT, request.title(), request.description(),
                request.domain(), request.importance(), (short) 7, statusFor(request.status()), provenance,
                false, x, y, (short) OffsetDateTime.now().getYear()));
        var project = projects.save(new Project(owner, graphNode, request.title(), request.description(), request.status()));
        concepts.forEach(concept -> edges.save(new GraphEdge(owner, graphNode, concept, RelationshipType.ENABLED, new BigDecimal("0.850"), provenance)));
        publication.invalidatePublished(subject);
        log.info("Project created projectId={} ownerId={}", project.getId(), owner.getId());
        return response(subject, project);
    }

    @Transactional(readOnly = true)
    public List<ProjectDtos.ProjectResponse> list(String subject) {
        return projects.findAllByOwnerSubjectOrderByCreatedAtAsc(subject).stream().map(project -> response(subject, project)).toList();
    }

    @Transactional(readOnly = true)
    public ProjectDtos.ProjectResponse get(String subject, UUID id) { return response(subject, requireProject(subject, id)); }

    @Transactional
    public ProjectDtos.ProjectResponse update(String subject, UUID id, ProjectDtos.UpdateProjectRequest request) {
        var project = requireProject(subject, id);
        if (project.getVersion() != request.version()) throw ApiException.conflict("STALE_UPDATE", "This project changed in another session");
        requireTransition(project.getStatus(), request.status());
        var startedAt = request.startedAt();
        var completedAt = request.completedAt();
        if ((request.status() == ProjectStatus.BUILDING || request.status() == ProjectStatus.COMPLETED) && startedAt == null) startedAt = LocalDate.now();
        if (request.status() == ProjectStatus.COMPLETED && completedAt == null) completedAt = LocalDate.now();
        if (request.status() != ProjectStatus.COMPLETED && request.status() != ProjectStatus.ARCHIVED) completedAt = null;
        if (startedAt != null && completedAt != null && completedAt.isBefore(startedAt)) {
            throw ApiException.badRequest("INVALID_PROJECT_DATES", "Completion date cannot be before the start date");
        }
        project.update(request.title(), request.description(), request.status(), startedAt, completedAt, request.repositoryUrl(), request.liveUrl());
        var node = project.getGraphNode();
        node.update(NodeType.PROJECT, request.title(), request.description(), node.getDomain(), node.getImportance(), node.getDepth(), statusFor(request.status()));
        projects.flush();
        nodes.flush();
        publication.invalidatePublished(subject);
        log.info("Project updated projectId={} ownerId={}", project.getId(), project.getOwner().getId());
        return response(subject, project);
    }

    @Transactional
    public void delete(String subject, UUID id) {
        var project = requireProject(subject, id);
        projects.delete(project);
        projects.flush();
        nodes.delete(project.getGraphNode());
        publication.invalidatePublished(subject);
        log.info("Project deleted projectId={} ownerId={}", project.getId(), project.getOwner().getId());
    }

    @Transactional
    public ProjectDtos.ProofResponse addProof(String subject, UUID projectId, ProjectDtos.CreateProofRequest request) {
        var project = requireProject(subject, projectId);
        var proof = proofs.save(new ProofOfWork(project.getOwner(), project, request.title(), request.type(), request.description(), request.url()));
        project.getGraphNode().markProof();
        publication.invalidatePublished(subject);
        log.info("Proof created proofId={} projectId={} ownerId={}", proof.getId(), project.getId(), project.getOwner().getId());
        return proofResponse(proof);
    }

    @Transactional(readOnly = true)
    public List<ProjectDtos.ProofResponse> proofs(String subject, UUID projectId) {
        requireProject(subject, projectId);
        return proofs.findAllByProjectIdAndOwnerSubject(projectId, subject).stream().map(this::proofResponse).toList();
    }

    @Transactional
    public void deleteProof(String subject, UUID proofId) {
        var proof = proofs.findByIdAndOwnerSubject(proofId, subject).orElseThrow(() -> ApiException.notFound("proof"));
        var project = proof.getProject();
        proofs.delete(proof);
        proofs.flush();
        if (proofs.countByProjectIdAndOwnerSubject(project.getId(), subject) == 0) project.getGraphNode().clearProof();
        publication.invalidatePublished(subject);
        log.info("Proof deleted proofId={} projectId={} ownerId={}", proof.getId(), project.getId(), project.getOwner().getId());
    }

    public Project requireProject(String subject, UUID id) {
        return projects.findByIdAndOwnerSubject(id, subject).orElseThrow(() -> ApiException.notFound("project"));
    }

    private GraphNode requireNode(String subject, UUID id) {
        return nodes.findByIdAndOwnerSubject(id, subject).orElseThrow(() -> ApiException.notFound("node"));
    }

    private ProjectDtos.ProjectResponse response(String subject, Project project) {
        var conceptIds = edges.findAllByOwnerSubjectOrderByCreatedAtAsc(subject).stream()
                .filter(edge -> edge.getSource().getId().equals(project.getGraphNode().getId()) || edge.getTarget().getId().equals(project.getGraphNode().getId()))
                .map(edge -> edge.getSource().getId().equals(project.getGraphNode().getId()) ? edge.getTarget().getId() : edge.getSource().getId())
                .toList();
        return new ProjectDtos.ProjectResponse(project.getId(), project.getGraphNode().getId(), project.getTitle(), project.getDescription(),
                project.getStatus(), project.getStartedAt(), project.getCompletedAt(), project.getRepositoryUrl(), project.getLiveUrl(),
                conceptIds, project.getCreatedAt(), project.getUpdatedAt(), project.getVersion());
    }

    private ProjectDtos.ProofResponse proofResponse(ProofOfWork proof) {
        return new ProjectDtos.ProofResponse(proof.getId(), proof.getProject().getId(), proof.getTitle(), proof.getType(),
                proof.getDescription(), proof.getUrl(), proof.getCreatedAt(), proof.getUpdatedAt(), proof.getVersion());
    }

    private NodeStatus statusFor(ProjectStatus status) {
        return switch (status) {
            case IDEA, PLANNED -> NodeStatus.EXPLORING;
            case BUILDING -> NodeStatus.ACTIVE;
            case COMPLETED -> NodeStatus.DEVELOPED;
            case ARCHIVED -> NodeStatus.ARCHIVED;
        };
    }

    private void requireTransition(ProjectStatus current, ProjectStatus requested) {
        if (current != requested && !ALLOWED_TRANSITIONS.get(current).contains(requested)) {
            throw ApiException.badRequest("INVALID_PROJECT_TRANSITION", "Project cannot move from " + current + " to " + requested);
        }
    }
}
