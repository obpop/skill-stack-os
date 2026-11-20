package com.skillstack.profile;

import com.skillstack.common.ApiException;
import com.skillstack.graph.domain.NodeType;
import com.skillstack.graph.persistence.GraphNodeRepository;
import com.skillstack.user.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;

@Service
public class ProfileService {
    private final UserProfileRepository profiles;
    private final GraphNodeRepository nodes;
    private final UserService users;
    private final ObjectMapper objectMapper;

    public ProfileService(UserProfileRepository profiles, GraphNodeRepository nodes, UserService users, ObjectMapper objectMapper) {
        this.profiles = profiles;
        this.nodes = nodes;
        this.users = users;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ProfileDtos.Response get(String subject) {
        var user = users.ensureUser(subject, subject);
        var profile = profiles.findByUserSubject(subject).orElseGet(() -> profiles.save(new UserProfile(user)));
        return response(profile);
    }

    @Transactional
    public ProfileDtos.Response upsert(String subject, ProfileDtos.UpsertRequest request) {
        var user = users.ensureUser(subject, request.displayName());
        var profile = profiles.findByUserSubject(subject).orElseGet(() -> new UserProfile(user));
        var focusIds = request.focusSkillIds() == null ? List.<UUID>of() : request.focusSkillIds().stream().distinct().toList();
        focusIds.forEach(id -> {
            var node = nodes.findByIdAndOwnerSubject(id, subject).orElseThrow(() -> ApiException.notFound("skill"));
            if (node.getType() != NodeType.SKILL) throw ApiException.badRequest("INVALID_FOCUS_SKILL", "Focus items must be owned skills");
        });
        if (request.complete()) validateCompletion(subject, request, focusIds);
        if (request.buildOnTheSide() && request.currentRole() != null && request.targetRole() != request.currentRole()) {
            throw ApiException.badRequest("INVALID_SIDE_BUILD", "Side-building keeps the current role as the target role");
        }
        profile.update(request.displayName(), request.city(), request.country(), request.currentRole(), request.targetRole(),
                request.buildOnTheSide(), request.timelineYears(), request.skillDirection(), writeIds(focusIds),
                request.workPreference(), request.helpTarget(), request.complete());
        user.rename(request.displayName().trim());
        if (request.complete()) user.completeOnboarding();
        return response(profiles.saveAndFlush(profile));
    }

    private void validateCompletion(String subject, ProfileDtos.UpsertRequest request, List<UUID> focusIds) {
        if (nodes.findAllByOwnerSubjectOrderByCreatedAtAsc(subject).stream().noneMatch(node -> node.getType() == NodeType.SKILL)) {
            throw ApiException.badRequest("SKILL_REQUIRED", "Add at least one skill before completing onboarding");
        }
        if (request.currentRole() == null || request.targetRole() == null || request.timelineYears() == null
                || request.skillDirection() == null || request.workPreference() == null || request.helpTarget() == null) {
            throw ApiException.badRequest("PROFILE_INCOMPLETE", "Complete every Direction question before viewing results");
        }
        if (request.skillDirection() == SkillDirection.SPECIALIZE && focusIds.size() != 1) {
            throw ApiException.badRequest("FOCUS_SKILL_REQUIRED", "Specializing requires exactly one focus skill");
        }
        if (request.skillDirection() == SkillDirection.COMBINE && focusIds.size() != 2) {
            throw ApiException.badRequest("FOCUS_SKILLS_REQUIRED", "Combining requires exactly two focus skills");
        }
    }

    private ProfileDtos.Response response(UserProfile profile) {
        return new ProfileDtos.Response(profile.getDisplayName(), profile.getCity(), profile.getCountry(),
                profile.getCurrentRole(), profile.getTargetRole(), profile.isBuildOnTheSide(), profile.getTimelineYears(),
                profile.getSkillDirection(), readIds(profile.getFocusSkillIds()), profile.getWorkPreference(), profile.getHelpTarget(),
                profile.getOnboardingCompletedAt(), profile.getOnboardingCompletedAt() != null, profile.getVersion());
    }

    private String writeIds(List<UUID> ids) {
        try { return objectMapper.writeValueAsString(ids); }
        catch (Exception exception) { throw new IllegalStateException("Unable to serialize focus skills", exception); }
    }

    private List<UUID> readIds(String json) {
        try { return objectMapper.readValue(json, new TypeReference<>() {}); }
        catch (Exception ignored) { return List.of(); }
    }
}
