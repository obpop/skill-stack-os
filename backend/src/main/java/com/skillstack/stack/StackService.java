package com.skillstack.stack;

import com.skillstack.common.ApiException;
import com.skillstack.graph.domain.*;
import com.skillstack.graph.persistence.GraphEdgeRepository;
import com.skillstack.graph.persistence.GraphNodeRepository;
import com.skillstack.profile.*;
import com.skillstack.project.persistence.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class StackService {
    private static final int HIGH_IMPORTANCE = 8;
    private static final Map<String, List<String>> ADJACENCY = Map.ofEntries(
            Map.entry("java", List.of("Distributed Systems", "Cloud Architecture", "API Design")),
            Map.entry("distributed systems", List.of("Cloud Architecture", "Observability", "System Design")),
            Map.entry("ai", List.of("Python", "Data Engineering", "AI Product Design")),
            Map.entry("artificial intelligence", List.of("Python", "Data Engineering", "AI Product Design")),
            Map.entry("design", List.of("User Research", "Prototyping", "Design Systems")),
            Map.entry("marketing", List.of("Copywriting", "Analytics", "Sales")),
            Map.entry("sales", List.of("Copywriting", "Negotiation", "Customer Research")),
            Map.entry("writing", List.of("Audience Research", "Editing", "Content Strategy")),
            Map.entry("finance", List.of("Data Analysis", "Financial Modeling", "Risk Management")),
            Map.entry("investing", List.of("Data Analysis", "Financial Modeling", "Economics")),
            Map.entry("video", List.of("Storytelling", "Motion Design", "Audience Growth")),
            Map.entry("music production", List.of("Sound Design", "Mixing", "Audience Growth")),
            Map.entry("software engineering", List.of("System Design", "Product Discovery", "Technical Writing"))
    );

    private final GraphNodeRepository nodes;
    private final GraphEdgeRepository edges;
    private final ProjectRepository projects;
    private final UserProfileRepository profiles;
    private final DevelopmentItemRepository catalog;
    private final ObjectMapper objectMapper;

    public StackService(GraphNodeRepository nodes, GraphEdgeRepository edges, ProjectRepository projects,
                        UserProfileRepository profiles, DevelopmentItemRepository catalog, ObjectMapper objectMapper) {
        this.nodes = nodes;
        this.edges = edges;
        this.projects = projects;
        this.profiles = profiles;
        this.catalog = catalog;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public StackDtos.Summary summary(String subject) {
        var context = context(subject);
        var strong = (int) context.skills.stream().filter(skill -> SkillStackRules.isStrong(skill, context.provenSkillIds.contains(skill.getId()))).count();
        var sentence = SkillStackRules.sentence(context.skills);
        var patterns = patterns(context);
        var stage = stage(context);
        var quadrants = pairwiseQuadrants(context);
        return new StackDtos.Summary(strong, SkillStackRules.rarity(strong), SkillStackRules.RARITY_CAVEAT,
                sentence, patterns, stage[0], stage[1], quadrants);
    }

    @Transactional(readOnly = true)
    public StackDtos.Discovery discover(String subject, StackDtos.DiscoverRequest request) {
        var skillIds = request.skillIds().stream().distinct().toList();
        if (skillIds.size() != request.skillIds().size() || skillIds.size() < 2 || skillIds.size() > 3) {
            throw ApiException.badRequest("INVALID_SKILL_SELECTION", "Choose 2–3 distinct skills");
        }
        var skills = skillIds.stream().map(id -> requireNode(subject, id, NodeType.SKILL)).toList();
        var interests = request.interestIds() == null ? List.<GraphNode>of() : request.interestIds().stream()
                .distinct().map(id -> requireNode(subject, id, NodeType.INTEREST)).toList();
        var sorted = skills.stream().sorted(Comparator.comparing(GraphNode::getTitle, String.CASE_INSENSITIVE_ORDER)).toList();
        var title = sorted.stream().map(GraphNode::getTitle).collect(Collectors.joining(" × "));
        var energy = interests.stream().anyMatch(GraphNode::isWouldDoForFree) || skills.stream().anyMatch(skill -> skill.getImportance() >= HIGH_IMPORTANCE);
        var demand = skills.stream().anyMatch(GraphNode::isMonetizable);
        var quadrant = quadrant("discovery-" + canonical(title), title, skills, energy, demand, interests);
        var profile = profiles.findByUserSubject(subject).orElse(null);
        var career = careerDirection(profile, title);
        return new StackDtos.Discovery(canonical(title), title,
                "This combines " + joinNatural(skills.stream().map(GraphNode::getTitle).toList()) + "; the label is based only on your energy and payability signals.",
                "Create a small case study that uses " + joinNatural(skills.stream().map(GraphNode::getTitle).toList()) + " for one real audience.",
                skills.stream().map(skill -> "Deepen " + skill.getTitle() + " through one scoped practice project.").toList(),
                career, quadrant, skillIds);
    }

    @Transactional(readOnly = true)
    public StackDtos.Develop develop(String subject) {
        var context = context(subject);
        var profile = profiles.findByUserSubject(subject).orElse(null);
        var next = chooseNextSkill(context, profile);
        var resources = catalog.findAll().stream()
                .filter(item -> matches(item.getRelatedSkills(), next.title()))
                .filter(item -> timelineMatches(item, profile))
                .sorted(Comparator.comparingInt(item -> resourceRank(item, profile)))
                .limit(18).map(item -> recommendation(item, next.title(), profile)).toList();
        var education = resources.stream().filter(item -> item.section().equals("EDUCATION")).toList();
        var networks = resources.stream().filter(item -> item.section().equals("NETWORK")).toList();
        var moves = roleMoves(profile, next);
        var build = buildMoves(context, next);
        var caveats = new ArrayList<String>();
        if (resources.isEmpty()) caveats.add("No verified catalog match is available yet; the actions below are local templates, not claimed courses or events.");
        caveats.add("Payability and demand signals are your hypotheses, not measured market demand.");
        return new StackDtos.Develop(next, education, networks, moves, build, caveats);
    }

    private Context context(String subject) {
        var allNodes = nodes.findAllByOwnerSubjectOrderByCreatedAtAsc(subject);
        var allEdges = edges.findAllByOwnerSubjectOrderByCreatedAtAsc(subject);
        var projectNodeIds = projects.findAllByOwnerSubjectOrderByCreatedAtAsc(subject).stream()
                .map(project -> project.getGraphNode().getId()).collect(Collectors.toSet());
        var proven = allEdges.stream().filter(edge -> projectNodeIds.contains(edge.getSource().getId()) || projectNodeIds.contains(edge.getTarget().getId()))
                .map(edge -> projectNodeIds.contains(edge.getSource().getId()) ? edge.getTarget() : edge.getSource())
                .filter(node -> node.getType() == NodeType.SKILL).map(GraphNode::getId).collect(Collectors.toSet());
        return new Context(allNodes.stream().filter(node -> node.getType() == NodeType.SKILL).toList(),
                allNodes.stream().filter(node -> node.getType() == NodeType.INTEREST).toList(), proven);
    }

    private List<StackDtos.Pattern> patterns(Context context) {
        var result = new ArrayList<StackDtos.Pattern>();
        if (context.skills.size() >= 3) result.add(new StackDtos.Pattern("Connector", "You have skills across " + context.skills.stream().map(GraphNode::getDomain).distinct().count() + " domains."));
        if (context.skills.stream().anyMatch(skill -> skill.isMonetizable() && skill.getImportance() >= HIGH_IMPORTANCE)) result.add(new StackDtos.Pattern("Commercial craft", "At least one important skill is marked ‘Someone would pay for this’."));
        if (context.interests.stream().anyMatch(GraphNode::isWouldDoForFree)) result.add(new StackDtos.Pattern("Energy-led", "An interest you would do for free can steer what you build next."));
        if (result.isEmpty() && !context.skills.isEmpty()) result.add(new StackDtos.Pattern("Builder in progress", "Your current stack is small enough to make the next skill choice deliberately."));
        return result.stream().limit(3).toList();
    }

    private String[] stage(Context context) {
        var strongMonetizable = context.skills.stream().filter(skill -> skill.isMonetizable() && SkillStackRules.isStrong(skill, context.provenSkillIds.contains(skill.getId()))).toList();
        if (strongMonetizable.isEmpty()) return new String[]{"ACQUIRE", "Build one monetizable skill toward Top 25% or project-backed proof."};
        if (strongMonetizable.stream().noneMatch(skill -> context.provenSkillIds.contains(skill.getId()))) return new String[]{"GET_PAID", "You report a strong monetizable skill; the next focus is testing it in a real project or paid context."};
        return new String[]{"BUILD_LEVERAGE", "A strong monetizable skill is project-backed; the recommended focus is making that proof reusable. This does not prove revenue."};
    }

    private List<StackDtos.Quadrant> pairwiseQuadrants(Context context) {
        var ranked = context.skills.stream().sorted(Comparator.comparingInt(GraphNode::getImportance).reversed()
                .thenComparing(GraphNode::getTitle, String.CASE_INSENSITIVE_ORDER)).limit(3).toList();
        var result = new ArrayList<StackDtos.Quadrant>();
        for (var i = 0; i < ranked.size(); i++) for (var j = i + 1; j < ranked.size(); j++) {
            var pair = List.of(ranked.get(i), ranked.get(j));
            var energy = pair.stream().anyMatch(skill -> skill.getImportance() >= HIGH_IMPORTANCE) || context.interests.stream().anyMatch(GraphNode::isWouldDoForFree);
            var demand = pair.stream().anyMatch(GraphNode::isMonetizable);
            var title = pair.get(0).getTitle() + " × " + pair.get(1).getTitle();
            result.add(quadrant(canonical(title), title, pair, energy, demand, context.interests));
        }
        return result;
    }

    private StackDtos.Quadrant quadrant(String id, String title, List<GraphNode> skills, boolean energy, boolean demand, List<GraphNode> interests) {
        var code = SkillStackRules.quadrant(energy, demand);
        var label = switch (code) { case "RARE_PAIR" -> "Rare Pair"; case "PASSION_PROJECT" -> "Passion Project"; case "GRIND" -> "Grind"; default -> "Dead End"; };
        var signals = new ArrayList<String>();
        if (skills.stream().anyMatch(skill -> skill.getImportance() >= HIGH_IMPORTANCE)) signals.add("Energy: a participating skill is important to you.");
        if (interests.stream().anyMatch(GraphNode::isWouldDoForFree)) signals.add("Energy: linked to an interest you would do for free.");
        if (!energy) signals.add("Energy: no high-energy signal is recorded yet.");
        if (demand) signals.add("Demand: you marked a participating skill as something someone would pay for.");
        else signals.add("Demand: no payability hypothesis is recorded yet.");
        return new StackDtos.Quadrant(id, title, label, energy, demand, signals, skills.stream().map(GraphNode::getId).toList());
    }

    private StackDtos.NextSkill chooseNextSkill(Context context, UserProfile profile) {
        if (context.skills.isEmpty()) return new StackDtos.NextSkill("Add your first skill", false, List.of("A recommendation needs at least one truthful starting skill."), List.of("ATTRACTIVENESS"), List.of());
        var ownedByCanonical = context.skills.stream().collect(Collectors.toMap(skill -> canonical(skill.getTitle()), skill -> skill, (a, b) -> a));
        record Candidate(String title, boolean owned, int score, List<UUID> sources) {}
        var candidates = new ArrayList<Candidate>();
        for (var skill : context.skills) {
            var proven = context.provenSkillIds.contains(skill.getId());
            if (!SkillStackRules.isStrong(skill, proven)) {
                var score = 60 + skill.getImportance() * 4 + (skill.isMonetizable() ? 12 : 0);
                if (profile != null && readFocusIds(profile).contains(skill.getId())) score += 25;
                candidates.add(new Candidate(skill.getTitle(), true, score, List.of(skill.getId())));
            }
            for (var adjacent : ADJACENCY.getOrDefault(canonical(skill.getTitle()), List.of())) {
                if (!ownedByCanonical.containsKey(canonical(adjacent))) candidates.add(new Candidate(adjacent, false,
                        45 + skill.getImportance() * 3 + (skill.isMonetizable() ? 8 : 0), List.of(skill.getId())));
            }
        }
        var chosen = candidates.stream().sorted(Comparator.comparingInt(Candidate::score).reversed()
                        .thenComparing(Candidate::title, String.CASE_INSENSITIVE_ORDER)).findFirst()
                .orElseGet(() -> new Candidate("Add a complementary skill", false, 0, context.skills.stream().limit(1).map(GraphNode::getId).toList()));
        var sourceNames = context.skills.stream().filter(skill -> chosen.sources().contains(skill.getId())).map(GraphNode::getTitle).toList();
        var reasons = new ArrayList<String>();
        reasons.add(chosen.owned() ? "It is already in your stack and still has room to strengthen." : "It is a curated complement to " + joinNatural(sourceNames) + ".");
        if (profile != null && profile.getSkillDirection() != null) reasons.add("It fits your " + profile.getSkillDirection().name().toLowerCase().replace('_', ' ') + " direction.");
        var levers = chosen.owned() && ownedByCanonical.get(canonical(chosen.title())).isMonetizable()
                ? List.of("INCOME", "ATTRACTIVENESS") : List.of("ATTRACTIVENESS", "LEVERAGE");
        return new StackDtos.NextSkill(chosen.title(), chosen.owned(), reasons, levers, chosen.sources());
    }

    private List<StackDtos.Recommendation> roleMoves(UserProfile profile, StackDtos.NextSkill next) {
        var role = profile == null || profile.getCurrentRole() == null ? CareerRole.EMPLOYEE : profile.getCurrentRole();
        var result = new ArrayList<StackDtos.Recommendation>();
        result.add(template("move-current", "MOVES", "Within your current role", switch (role) {
            case STUDENT -> "Find a small internship or campus project using " + next.title();
            case EMPLOYEE -> "Ask to spend one scoped work cycle using " + next.title();
            case FREELANCER -> "Package a small paid service that pairs your current craft with " + next.title();
            case ENTREPRENEUR -> "Test whether learning " + next.title() + " beats partnering or outsourcing for the next milestone";
        }, next, "INCOME"));
        if (profile != null && profile.getTargetRole() != null && profile.getTargetRole() != role) {
            result.add(template("move-target", "MOVES", "Heading toward " + pretty(profile.getTargetRole().name()),
                    "Collect one project-backed example of " + next.title() + " that is legible to a " + pretty(profile.getTargetRole().name()) + ".", next, "ATTRACTIVENESS"));
        }
        if (profile != null && profile.isBuildOnTheSide()) result.add(template("move-side", "MOVES", "Build on the side", "Turn " + next.title() + " into one weekend-sized offer and ask three people for evidence, not compliments.", next, "LEVERAGE"));
        return result;
    }

    private List<StackDtos.Recommendation> buildMoves(Context context, StackDtos.NextSkill next) {
        var anchor = context.skills.stream().filter(skill -> next.sourceSkillIds().contains(skill.getId())).findFirst().map(GraphNode::getTitle).orElse("your current skill");
        return List.of(
                template("build-case", "BUILD", "Case study", "Build a small case study combining " + anchor + " and " + next.title() + ", then explain the tradeoffs.", next, "ATTRACTIVENESS"),
                template("build-public", "BUILD", "Shareable proof", "Publish a demo, repository, tutorial or visual walkthrough that makes your use of " + next.title() + " inspectable.", next, "LEVERAGE")
        );
    }

    private StackDtos.Recommendation template(String id, String section, String title, String reason, StackDtos.NextSkill next, String lever) {
        return new StackDtos.Recommendation(id, section, "ACTION", title, reason, "No direct purchase required", "Online or local", lever, null);
    }

    private StackDtos.Recommendation recommendation(DevelopmentItem item, String skill, UserProfile profile) {
        var format = item.getFormat() == DevelopmentItem.Format.ONLINE ? "Online" : item.getCity() == null ? "In person" : item.getCity();
        return new StackDtos.Recommendation(item.getId(), item.getCategory().name(), item.getSubcategory() == null ? null : item.getSubcategory().name(),
                item.getTitle(), "A verified catalog option related to " + skill + ".", pretty(item.getCost().name()), format, item.getLever().name(), item.getUrl());
    }

    private boolean timelineMatches(DevelopmentItem item, UserProfile profile) {
        var years = profile == null || profile.getTimelineYears() == null ? 3 : profile.getTimelineYears();
        return years >= item.getMinTimelineYears() && years <= item.getMaxTimelineYears();
    }

    private int resourceRank(DevelopmentItem item, UserProfile profile) {
        var years = profile == null || profile.getTimelineYears() == null ? 3 : profile.getTimelineYears();
        var rank = item.getSubcategory() == DevelopmentItem.Subcategory.FORMAL && years <= 3 ? 20 : 0;
        if (profile != null && profile.getWorkPreference() == WorkPreference.REMOTE && item.getFormat() == DevelopmentItem.Format.ONLINE) rank -= 10;
        if (profile != null && profile.getWorkPreference() == WorkPreference.STAY_IN_CITY && profile.getCity() != null && profile.getCity().equalsIgnoreCase(item.getCity())) rank -= 12;
        return rank;
    }

    private boolean matches(String json, String value) {
        try { return objectMapper.readValue(json, new TypeReference<List<String>>() {}).stream().anyMatch(item -> canonical(item).equals(canonical(value))); }
        catch (Exception ignored) { return false; }
    }

    private List<UUID> readFocusIds(UserProfile profile) {
        try { return objectMapper.readValue(profile.getFocusSkillIds(), new TypeReference<>() {}); }
        catch (Exception ignored) { return List.of(); }
    }

    private GraphNode requireNode(String subject, UUID id, NodeType type) {
        var node = nodes.findByIdAndOwnerSubject(id, subject).orElseThrow(() -> ApiException.notFound(type.name().toLowerCase()));
        if (node.getType() != type) throw ApiException.badRequest("INVALID_" + type.name(), "Choose only owned " + type.name().toLowerCase() + " nodes");
        return node;
    }

    private String careerDirection(UserProfile profile, String intersection) {
        if (profile == null || profile.getCurrentRole() == null) return "Test where " + intersection + " solves a concrete problem before choosing a role move.";
        if (profile.getTargetRole() != null && profile.getTargetRole() != profile.getCurrentRole()) return "Use one project to show how " + intersection + " transfers from " + pretty(profile.getCurrentRole().name()) + " toward " + pretty(profile.getTargetRole().name()) + ".";
        return "Look for a small " + pretty(profile.getCurrentRole().name()) + " move where " + intersection + " changes an outcome.";
    }

    private String canonical(String value) { return value.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim(); }
    private String pretty(String value) { var clean = value.toLowerCase(Locale.ROOT).replace('_', ' '); return Character.toUpperCase(clean.charAt(0)) + clean.substring(1); }
    private String joinNatural(List<String> values) { return values.size() <= 1 ? String.join("", values) : String.join(", ", values.subList(0, values.size() - 1)) + " and " + values.getLast(); }
    private record Context(List<GraphNode> skills, List<GraphNode> interests, Set<UUID> provenSkillIds) {}
}
