package com.skillstack.graph.application;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.skillstack.common.ApiException;
import com.skillstack.graph.api.GraphDtos;
import com.skillstack.graph.domain.*;
import com.skillstack.graph.persistence.GraphEdgeRepository;
import com.skillstack.graph.persistence.GraphNodeRepository;
import com.skillstack.intersection.IntersectionDecisionRepository;
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
import java.time.ZoneOffset;
import java.util.*;

@Service
public class GraphService {
    private static final Logger log = LoggerFactory.getLogger(GraphService.class);
    private static final Set<NodeType> USER_EDITABLE_NODE_TYPES = EnumSet.of(
            NodeType.IDENTITY, NodeType.INTEREST, NodeType.SKILL, NodeType.KNOWLEDGE, NodeType.VALUE,
            NodeType.GOAL, NodeType.IDEA, NodeType.PERSON, NodeType.BOOK, NodeType.INDUSTRY);
    private static final Map<String, double[]> DOMAIN_CENTERS = Map.ofEntries(
            Map.entry("engineering", new double[]{-27, 12}), Map.entry("ai", new double[]{-7, 22}),
            Map.entry("business", new double[]{19, 19}), Map.entry("economics", new double[]{28, -1}),
            Map.entry("ideas", new double[]{5, 2}), Map.entry("culture", new double[]{18, -22}),
            Map.entry("world", new double[]{-9, -24}), Map.entry("health", new double[]{-29, -15}),
            Map.entry("communication", new double[]{-4, -8})
    );

    private final GraphNodeRepository nodes;
    private final GraphEdgeRepository edges;
    private final ProjectRepository projects;
    private final ProofOfWorkRepository proofs;
    private final IntersectionDecisionRepository intersections;
    private final UserService users;
    private final ObjectMapper objectMapper;
    private final PublicationService publication;

    public GraphService(GraphNodeRepository nodes, GraphEdgeRepository edges, ProjectRepository projects,
                        ProofOfWorkRepository proofs, IntersectionDecisionRepository intersections,
                        UserService users, ObjectMapper objectMapper, PublicationService publication) {
        this.nodes = nodes;
        this.edges = edges;
        this.projects = projects;
        this.proofs = proofs;
        this.intersections = intersections;
        this.users = users;
        this.objectMapper = objectMapper;
        this.publication = publication;
    }

    @Transactional
    public GraphDtos.GraphResponse graph(String subject) {
        var user = users.ensureUser(subject, subject);
        var graphNodes = nodes.findAllByOwnerSubjectOrderByCreatedAtAsc(subject);
        var graphEdges = edges.findAllByOwnerSubjectOrderByCreatedAtAsc(subject);
        var projectRows = projects.findAllByOwnerSubjectOrderByCreatedAtAsc(subject);
        var proofRows = proofs.findAllByOwnerSubjectOrderByCreatedAtAsc(subject);
        var edgeRows = graphEdges.stream().map(GraphDtos.EdgeResponse::from).toList();
        var projectSummaries = projectRows.stream().map(project -> new GraphDtos.ProjectSummary(
                project.getId(), project.getGraphNode().getId(), project.getTitle(), project.getDescription(),
                project.getStatus().name(), project.getStartedAt(), project.getCompletedAt(), project.getRepositoryUrl(), project.getLiveUrl(), graphEdges.stream()
                        .filter(edge -> edge.getSource().getId().equals(project.getGraphNode().getId()) || edge.getTarget().getId().equals(project.getGraphNode().getId()))
                        .map(edge -> edge.getSource().getId().equals(project.getGraphNode().getId()) ? edge.getTarget().getId() : edge.getSource().getId())
                        .toList(), project.getCreatedAt(), project.getUpdatedAt(), project.getVersion())).toList();
        var proofSummaries = proofRows.stream().map(proof -> new GraphDtos.ProofSummary(
                proof.getId(), proof.getProject().getId(), proof.getTitle(), proof.getType().name(), proof.getDescription(), proof.getUrl())).toList();
        var intersectionSummaries = intersections.findAllByOwnerSubject(subject).stream().map(decision -> new GraphDtos.IntersectionSummary(
                decision.getId(), decision.getDefinitionKey(), decision.getTitle(), decision.getDecision().name(),
                parseIds(decision.getContributingNodeIds()), decision.getResultingProject() == null ? null : decision.getResultingProject().getId())).toList();

        return new GraphDtos.GraphResponse(
                new GraphDtos.UserSummary(user.getId(), user.getSubject(), user.getDisplayName(), user.isOnboardingComplete(), user.getDefaultDomain()),
                graphNodes.stream().map(node -> GraphDtos.NodeResponse.from(node, provenByProjects(node, graphEdges, projectRows))).toList(), edgeRows, projectSummaries, proofSummaries,
                intersectionSummaries,
                new GraphDtos.GraphMetadata(graphNodes.size(), graphEdges.size(), OffsetDateTime.now(ZoneOffset.UTC), "postgresql"));
    }

    @Transactional
    public GraphDtos.NodeResponse createNode(String subject, GraphDtos.CreateNodeRequest request) {
        requireUserEditableType(request.type());
        var owner = users.ensureUser(subject, subject);
        var position = nextPosition(subject, request.domain());
        var node = new GraphNode(owner, null, request.type(), request.title(), request.description(), request.domain(),
                request.importance(), request.depth(), request.status(), Provenance.USER_CREATED, false,
                position[0], position[1], (short) OffsetDateTime.now().getYear());
        node.updateSkillStack(request.level(), request.monetizable(), request.learnOrOutsource(), request.wouldDoForFree());
        var saved = nodes.save(node);
        publication.invalidatePublished(subject);
        log.info("Graph node created nodeId={} ownerId={}", saved.getId(), owner.getId());
        return GraphDtos.NodeResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public GraphDtos.NodeResponse getNode(String subject, UUID id) {
        return GraphDtos.NodeResponse.from(requireNode(subject, id));
    }

    @Transactional
    public GraphDtos.NodeResponse updateNode(String subject, UUID id, GraphDtos.UpdateNodeRequest request) {
        var node = requireNode(subject, id);
        requireVersion(node.getVersion(), request.version());
        validateNodeUpdate(node, request);
        node.update(request.type(), request.title(), request.description(), request.domain(), request.importance(), request.depth(), request.status());
        node.updateSkillStack(request.level(), request.monetizable(), request.learnOrOutsource(), request.wouldDoForFree());
        var saved = nodes.saveAndFlush(node);
        publication.invalidatePublished(subject);
        log.info("Graph node updated nodeId={} ownerId={}", saved.getId(), saved.getOwner().getId());
        return GraphDtos.NodeResponse.from(saved);
    }

    @Transactional
    public void deleteNode(String subject, UUID id) {
        var node = requireNode(subject, id);
        nodes.delete(node);
        publication.invalidatePublished(subject);
        log.info("Graph node deleted nodeId={} ownerId={}", node.getId(), node.getOwner().getId());
    }

    @Transactional
    public GraphDtos.EdgeResponse createEdge(String subject, GraphDtos.CreateEdgeRequest request) {
        if (request.source().equals(request.target())) throw ApiException.badRequest("SELF_RELATIONSHIP", "A node cannot relate to itself");
        var source = requireNode(subject, request.source());
        var target = requireNode(subject, request.target());
        if (edges.existsByOwnerSubjectAndSourceIdAndTargetIdAndRelationshipType(subject, source.getId(), target.getId(), request.type()) ||
                edges.existsByOwnerSubjectAndSourceIdAndTargetIdAndRelationshipType(subject, target.getId(), source.getId(), request.type())) {
            throw ApiException.conflict("RELATIONSHIP_EXISTS", "This relationship already exists");
        }
        var edge = new GraphEdge(source.getOwner(), source, target, request.type(), request.strength(), Provenance.USER_CREATED);
        var saved = edges.save(edge);
        publication.invalidatePublished(subject);
        log.info("Graph edge created edgeId={} ownerId={}", saved.getId(), saved.getOwner().getId());
        return GraphDtos.EdgeResponse.from(saved);
    }

    @Transactional
    public GraphDtos.EdgeResponse updateEdge(String subject, UUID id, GraphDtos.UpdateEdgeRequest request) {
        var edge = edges.findByIdAndOwnerSubject(id, subject).orElseThrow(() -> ApiException.notFound("edge"));
        requireVersion(edge.getVersion(), request.version());
        edge.update(request.type(), request.strength());
        var saved = edges.saveAndFlush(edge);
        publication.invalidatePublished(subject);
        log.info("Graph edge updated edgeId={} ownerId={}", saved.getId(), saved.getOwner().getId());
        return GraphDtos.EdgeResponse.from(saved);
    }

    @Transactional
    public void deleteEdge(String subject, UUID id) {
        var edge = edges.findByIdAndOwnerSubject(id, subject).orElseThrow(() -> ApiException.notFound("edge"));
        edges.delete(edge);
        publication.invalidatePublished(subject);
        log.info("Graph edge deleted edgeId={} ownerId={}", edge.getId(), edge.getOwner().getId());
    }

    @Transactional(readOnly = true)
    public GraphDtos.SearchResponse search(String subject, String query) {
        var clean = query == null ? "" : query.trim();
        if (clean.length() > 180) throw ApiException.badRequest("SEARCH_QUERY_TOO_LONG", "Search query must be 180 characters or fewer");
        var results = clean.isEmpty() ? List.<GraphDtos.NodeResponse>of() : nodes.search(subject, clean).stream().limit(30).map(GraphDtos.NodeResponse::from).toList();
        return new GraphDtos.SearchResponse(results, Map.of("query", clean, "count", results.size(), "strategy", "deterministic-postgresql"));
    }

    public GraphNode requireNode(String subject, UUID id) {
        return nodes.findByIdAndOwnerSubject(id, subject).orElseThrow(() -> ApiException.notFound("node"));
    }

    private double[] nextPosition(String subject, String domain) {
        var center = DOMAIN_CENTERS.getOrDefault(domain.toLowerCase(), new double[]{0, 0});
        var index = nodes.countByOwnerSubject(subject);
        var angle = index * 2.399963;
        var ring = 5 + (index % 7) * 1.4;
        return new double[]{center[0] + Math.cos(angle) * ring, center[1] + Math.sin(angle) * ring * .76};
    }

    private void requireVersion(long current, long supplied) {
        if (current != supplied) throw ApiException.conflict("STALE_UPDATE", "This item changed in another session. Refresh and try again.");
    }

    private void requireUserEditableType(NodeType type) {
        if (!USER_EDITABLE_NODE_TYPES.contains(type)) {
            throw ApiException.badRequest("NODE_TYPE_MANAGED", "This node type is managed through its dedicated workflow");
        }
    }

    private void validateNodeUpdate(GraphNode node, GraphDtos.UpdateNodeRequest request) {
        if (USER_EDITABLE_NODE_TYPES.contains(node.getType())) {
            requireUserEditableType(request.type());
            return;
        }
        var semanticFieldsChanged = node.getType() != request.type()
                || !node.getTitle().equals(request.title().trim())
                || !Objects.equals(node.getDescription(), request.description() == null || request.description().isBlank() ? null : request.description().trim())
                || !node.getDomain().equals(request.domain().trim().toLowerCase())
                || node.getStatus() != request.status();
        if (semanticFieldsChanged) {
            throw ApiException.badRequest("NODE_TYPE_MANAGED", "Edit this node through its dedicated workflow");
        }
    }

    private List<UUID> parseIds(String json) {
        try { return objectMapper.readValue(json, new TypeReference<>() {}); }
        catch (Exception ignored) { return List.of(); }
    }

    private List<UUID> provenByProjects(GraphNode node, List<GraphEdge> graphEdges,
                                        List<com.skillstack.project.domain.Project> projectRows) {
        if (node.getType() != NodeType.SKILL) return List.of();
        var projectByNode = projectRows.stream().collect(java.util.stream.Collectors.toMap(
                project -> project.getGraphNode().getId(), com.skillstack.project.domain.Project::getId));
        return graphEdges.stream()
                .filter(edge -> edge.getSource().getId().equals(node.getId()) || edge.getTarget().getId().equals(node.getId()))
                .map(edge -> edge.getSource().getId().equals(node.getId()) ? edge.getTarget().getId() : edge.getSource().getId())
                .map(projectByNode::get)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }
}
