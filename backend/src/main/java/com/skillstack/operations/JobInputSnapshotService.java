package com.skillstack.operations;

import com.skillstack.common.ApiException;
import com.skillstack.graph.domain.NodeType;
import com.skillstack.graph.persistence.GraphNodeRepository;
import com.skillstack.graph.persistence.GraphEdgeRepository;
import com.skillstack.profile.UserProfileRepository;
import com.skillstack.project.persistence.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

@Service
public class JobInputSnapshotService {
    private final GraphNodeRepository nodes;
    private final GraphEdgeRepository edges;
    private final UserProfileRepository profiles;
    private final ProjectRepository projects;

    public JobInputSnapshotService(GraphNodeRepository nodes, GraphEdgeRepository edges,
                                   UserProfileRepository profiles, ProjectRepository projects) {
        this.nodes = nodes;
        this.edges = edges;
        this.profiles = profiles;
        this.projects = projects;
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public String snapshot(String subject, List<UUID> requestedSkillIds) {
        var selected = requestedSkillIds == null ? List.<UUID>of() : requestedSkillIds.stream().distinct().sorted().toList();
        if (requestedSkillIds != null && selected.size() != requestedSkillIds.size()) {
            throw ApiException.badRequest("DUPLICATE_SKILL", "Choose distinct skills");
        }
        if (selected.size() > 3) throw ApiException.badRequest("TOO_MANY_SKILLS", "Choose at most three skills");
        for (var id : selected) {
            var node = nodes.findByIdAndOwnerSubject(id, subject).orElseThrow(() -> ApiException.notFound("skill"));
            if (node.getType() != NodeType.SKILL) throw ApiException.badRequest("INVALID_SKILL", "Analysis inputs must be owned skills");
        }

        var parts = new ArrayList<String>();
        parts.add("phase4-job-v1:recommendation-phase3-v1:catalog-v4");
        parts.add("selection:" + selected.stream().map(UUID::toString).reduce((a, b) -> a + "," + b).orElse("default"));
        nodes.findAllByOwnerSubjectOrderByCreatedAtAsc(subject).stream()
                .sorted(Comparator.comparing(node -> node.getId().toString()))
                .forEach(node -> parts.add("node:" + node.getId() + ":" + node.getVersion() + ":" + node.getType()
                        + ":" + node.getTitle() + ":" + node.getDomain() + ":" + node.getStatus()
                        + ":" + node.getSkillLevel() + ":" + node.getImportance() + ":" + node.getDepth()
                        + ":" + node.isProofOfWork() + ":" + node.isMonetizable() + ":" + node.isWouldDoForFree()));
        edges.findAllByOwnerSubjectOrderByCreatedAtAsc(subject).stream()
                .sorted(Comparator.comparing(edge -> edge.getId().toString()))
                .forEach(edge -> parts.add("edge:" + edge.getId() + ":" + edge.getVersion() + ":"
                        + edge.getSource().getId() + ":" + edge.getTarget().getId() + ":" + edge.getRelationshipType()));
        profiles.findByUserSubject(subject).ifPresent(profile -> parts.add("profile:" + profile.getVersion() + ":"
                + profile.getCurrentRole() + ":" + profile.getTargetRole() + ":" + profile.getSkillDirection()
                + ":" + profile.getTimelineYears() + ":" + profile.getWorkPreference() + ":" + profile.isBuildOnTheSide()
                + ":" + profile.getHelpTarget() + ":" + profile.getFocusSkillIds() + ":" + profile.getCity() + ":" + profile.getCountry()));
        projects.findAllByOwnerSubjectOrderByCreatedAtAsc(subject).stream()
                .sorted(Comparator.comparing(project -> project.getId().toString()))
                .forEach(project -> parts.add("project:" + project.getId() + ":" + project.getVersion()));
        return sha256(String.join("|", parts));
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception problem) {
            throw new IllegalStateException("Unable to fingerprint job input", problem);
        }
    }
}
