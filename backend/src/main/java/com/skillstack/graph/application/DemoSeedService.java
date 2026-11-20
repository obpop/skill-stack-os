package com.skillstack.graph.application;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import tools.jackson.databind.ObjectMapper;
import com.skillstack.graph.domain.*;
import com.skillstack.graph.persistence.GraphEdgeRepository;
import com.skillstack.graph.persistence.GraphNodeRepository;
import com.skillstack.project.domain.*;
import com.skillstack.project.persistence.ProjectRepository;
import com.skillstack.project.persistence.ProofOfWorkRepository;
import com.skillstack.user.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

@Component
@ConditionalOnProperty(name = "skillstack.demo-seed-enabled", havingValue = "true")
public class DemoSeedService implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(DemoSeedService.class);
    private final ObjectMapper mapper;
    private final UserService users;
    private final GraphNodeRepository nodes;
    private final GraphEdgeRepository edges;
    private final ProjectRepository projects;
    private final ProofOfWorkRepository proofs;

    public DemoSeedService(ObjectMapper mapper, UserService users, GraphNodeRepository nodes, GraphEdgeRepository edges,
                           ProjectRepository projects, ProofOfWorkRepository proofs) {
        this.mapper = mapper;
        this.users = users;
        this.nodes = nodes;
        this.edges = edges;
        this.projects = projects;
        this.proofs = proofs;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws Exception {
        var owner = users.ensureUser("demo-user", "Olli");
        if (nodes.countByOwnerSubject("demo-user") > 0) {
            if (!owner.isOnboardingComplete()) users.completeOnboarding("demo-user");
            return;
        }
        var seed = mapper.readValue(new ClassPathResource("seed/demo-universe.json").getInputStream(), SeedUniverse.class);
        var byExternalKey = new HashMap<String, GraphNode>();
        for (var source : seed.nodes()) {
            var node = nodes.save(new GraphNode(owner, source.id(), NodeType.valueOf(source.type()), source.name(), source.description(),
                    source.domain(), source.importance(), source.depth(), status(source), Provenance.DEMO,
                    Boolean.TRUE.equals(source.proofOfWork()), source.x(), source.y(), source.year()));
            byExternalKey.put(source.id(), node);
        }
        for (var source : seed.edges()) {
            var from = byExternalKey.get(source.source());
            var to = byExternalKey.get(source.target());
            if (from == null || to == null || from.getId().equals(to.getId())) continue;
            edges.save(new GraphEdge(owner, from, to, RelationshipType.valueOf(source.type().toUpperCase()),
                    BigDecimal.valueOf(source.strength()), Provenance.DEMO));
        }
        for (var source : seed.projects()) {
            var projectNode = byExternalKey.get(source.id());
            if (projectNode == null) continue;
            var project = projects.save(new Project(owner, projectNode, source.name(), source.description(), ProjectStatus.COMPLETED));
            proofs.save(new ProofOfWork(owner, project, source.name() + " prototype", ProofType.EXPERIMENT,
                    "Imported Phase 1 proof of work", null));
        }
        users.completeOnboarding("demo-user");
        log.info("Seeded demo universe nodes={} edges={} projects={}", seed.nodes().size(), seed.edges().size(), seed.projects().size());
    }

    private NodeStatus status(NodeSeed node) {
        if (node.type().equals("PROJECT") || Boolean.TRUE.equals(node.proofOfWork())) return NodeStatus.DEVELOPED;
        if (node.importance() >= 9) return NodeStatus.ACTIVE;
        return NodeStatus.EXPLORING;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SeedUniverse(List<NodeSeed> nodes, List<EdgeSeed> edges, List<ProjectSeed> projects) {
        public SeedUniverse {
            nodes = nodes == null ? List.of() : nodes;
            edges = edges == null ? List.of() : edges;
            projects = projects == null ? List.of() : projects;
        }
    }
    public record NodeSeed(String id, String type, String name, String domain, short importance, short depth,
                           short activity, Boolean proofOfWork, String description, short year, double x, double y) {}
    public record EdgeSeed(String id, String source, String target, String type, double strength, String provenance) {}
    public record ProjectSeed(String id, String name, String description, List<String> domains, List<String> concepts, short year) {}
}
