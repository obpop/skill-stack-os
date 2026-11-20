package com.skillstack.importer;

import com.skillstack.common.ApiException;
import com.skillstack.graph.domain.*;
import com.skillstack.graph.persistence.GraphNodeRepository;
import com.skillstack.intelligence.AiPolicy;
import com.skillstack.intelligence.AiCostGate;
import com.skillstack.intelligence.AiOperation;
import com.skillstack.intelligence.AiOperationRepository;
import com.skillstack.intelligence.AiProvider;
import com.skillstack.intelligence.AiProviderExecutor;
import com.skillstack.intelligence.AiResponseValidator;
import com.skillstack.project.application.ProjectService;
import com.skillstack.user.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.*;

@Service
public class ImportService {
    private static final long MAX_BYTES = 2L * 1024 * 1024;
    private static final Map<String, NodeType> DICTIONARY = Map.ofEntries(
            Map.entry("java", NodeType.SKILL), Map.entry("spring boot", NodeType.SKILL), Map.entry("spring", NodeType.SKILL),
            Map.entry("distributed systems", NodeType.SKILL), Map.entry("software engineering", NodeType.SKILL),
            Map.entry("typescript", NodeType.SKILL), Map.entry("javascript", NodeType.SKILL), Map.entry("react", NodeType.SKILL),
            Map.entry("next js", NodeType.SKILL), Map.entry("python", NodeType.SKILL), Map.entry("artificial intelligence", NodeType.SKILL),
            Map.entry("machine learning", NodeType.SKILL), Map.entry("data analysis", NodeType.SKILL), Map.entry("design", NodeType.SKILL),
            Map.entry("user research", NodeType.SKILL), Map.entry("marketing", NodeType.SKILL), Map.entry("sales", NodeType.SKILL),
            Map.entry("writing", NodeType.SKILL), Map.entry("finance", NodeType.SKILL), Map.entry("investing", NodeType.SKILL),
            Map.entry("video", NodeType.SKILL), Map.entry("music production", NodeType.SKILL), Map.entry("economics", NodeType.INTEREST),
            Map.entry("technology", NodeType.INTEREST), Map.entry("education", NodeType.INTEREST), Map.entry("climate", NodeType.INTEREST),
            Map.entry("health", NodeType.INTEREST), Map.entry("culture", NodeType.INTEREST), Map.entry("entrepreneurship", NodeType.INTEREST)
    );

    private final ImportJobRepository jobs;
    private final GraphNodeRepository nodes;
    private final UserService users;
    private final ProjectService projectService;
    private final ObjectMapper objectMapper;
    private final AiProvider aiProvider;
    private final AiPolicy aiPolicy;
    private final AiCostGate aiCostGate;
    private final AiOperationRepository aiOperations;
    private final AiProviderExecutor aiExecutor;

    public ImportService(ImportJobRepository jobs, GraphNodeRepository nodes, UserService users,
                         ProjectService projectService, ObjectMapper objectMapper,
                         AiProvider aiProvider, AiPolicy aiPolicy, AiCostGate aiCostGate,
                         AiOperationRepository aiOperations, AiProviderExecutor aiExecutor) {
        this.jobs = jobs; this.nodes = nodes; this.users = users; this.projectService = projectService; this.objectMapper = objectMapper;
        this.aiProvider = aiProvider; this.aiPolicy = aiPolicy;
        this.aiCostGate = aiCostGate; this.aiOperations = aiOperations;
        this.aiExecutor = aiExecutor;
    }

    @Transactional
    public ImportDtos.Job create(String subject, MultipartFile file) {
        if (file == null || file.isEmpty()) throw ApiException.badRequest("IMPORT_EMPTY", "Choose a non-empty .txt or .md file");
        if (file.getSize() > MAX_BYTES) throw ApiException.badRequest("IMPORT_TOO_LARGE", "Import files must be 2 MB or smaller");
        var name = Optional.ofNullable(file.getOriginalFilename()).orElse("").toLowerCase(Locale.ROOT);
        var sourceType = name.endsWith(".md") ? ImportJob.SourceType.MARKDOWN : name.endsWith(".txt") ? ImportJob.SourceType.TEXT : null;
        if (sourceType == null) throw ApiException.badRequest("IMPORT_TYPE_UNSUPPORTED", "Only .txt and .md files are supported");
        var job = jobs.save(new ImportJob(users.ensureUser(subject, subject), sourceType));
        try {
            var decoder = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT);
            var text = decoder.decode(ByteBuffer.wrap(file.getBytes())).toString();
            var proposals = extract(subject, text);
            job.extracted(objectMapper.writeValueAsString(proposals));
            return response(jobs.saveAndFlush(job));
        } catch (java.nio.charset.CharacterCodingException problem) {
            job.failed("INVALID_UTF8"); jobs.saveAndFlush(job);
            throw ApiException.badRequest("INVALID_UTF8", "The file must contain valid UTF-8 text");
        } catch (ApiException problem) {
            throw problem;
        } catch (Exception problem) {
            job.failed("IMPORT_PARSE_FAILED"); jobs.saveAndFlush(job);
            throw ApiException.badRequest("IMPORT_PARSE_FAILED", "The text could not be parsed safely");
        }
    }

    @Transactional(readOnly = true)
    public ImportDtos.Job get(String subject, UUID id) { return response(requireJob(subject, id)); }

    @Transactional
    public ImportDtos.AcceptResponse accept(String subject, UUID id, ImportDtos.AcceptRequest request) {
        var job = requireJob(subject, id);
        if (job.getStatus() == ImportJob.Status.REVIEWED) return new ImportDtos.AcceptResponse(id, 0, 0, List.of(), true);
        if (job.getStatus() != ImportJob.Status.EXTRACTED) throw ApiException.conflict("IMPORT_NOT_READY", "This import is not ready for review");
        var proposals = readProposals(job.getProposedItems());
        var byId = proposals.stream().collect(java.util.stream.Collectors.toMap(ImportDtos.Proposal::id, proposal -> proposal));
        if (request.items().stream().map(ImportDtos.AcceptedItem::id).distinct().count() != request.items().size()) {
            throw ApiException.badRequest("DUPLICATE_IMPORT_ITEM", "Each reviewed proposal can be accepted once");
        }
        var existing = nodes.findAllByOwnerSubjectOrderByCreatedAtAsc(subject);
        var nodeIds = new ArrayList<UUID>();
        var added = 0; var merged = 0;
        for (var item : request.items()) {
            var proposal = byId.get(item.id());
            if (proposal == null || proposal.type() != item.type() || (item.type() != NodeType.SKILL && item.type() != NodeType.INTEREST && item.type() != NodeType.PROJECT)) {
                throw ApiException.badRequest("INVALID_IMPORT_ITEM", "Every accepted item must belong to this reviewed import");
            }
            var duplicate = existing.stream().filter(node -> node.getType() == item.type() && canonical(node.getTitle()).equals(canonical(item.title()))).findFirst();
            if (duplicate.isPresent()) {
                var node = duplicate.get();
                if (node.getType() == NodeType.SKILL && stronger(item.level(), node.getSkillLevel())) {
                    node.updateSkillStack(item.level(), node.isMonetizable() || item.monetizable(), node.getLearnOrOutsource(), false);
                }
                if (node.getType() == NodeType.INTEREST && item.wouldDoForFree()) node.updateSkillStack(null, false, null, true);
                nodeIds.add(node.getId()); merged++; continue;
            }
            if (item.type() == NodeType.PROJECT) {
                var project = projectService.createImported(subject, item.title());
                nodeIds.add(project.graphNodeId()); added++; continue;
            }
            var owner = users.require(subject);
            var index = nodes.countByOwnerSubject(subject) + added;
            var angle = index * 2.399963;
            var node = new GraphNode(owner, null, item.type(), item.title(), "Imported after your review", "ideas",
                    (short) 5, (short) 3, NodeStatus.EXPLORING, Provenance.IMPORTED, false,
                    Math.cos(angle) * (8 + index % 8), Math.sin(angle) * (6 + index % 6), (short) OffsetDateTime.now().getYear());
            node.updateSkillStack(item.level(), item.monetizable(), null, item.wouldDoForFree());
            nodes.save(node); existing.add(node); nodeIds.add(node.getId()); added++;
        }
        job.reviewed(); jobs.saveAndFlush(job);
        return new ImportDtos.AcceptResponse(id, added, merged, nodeIds, false);
    }

    private List<ImportDtos.Proposal> extract(String subject, String rawText) {
        var text = canonical(rawText);
        var existing = nodes.findAllByOwnerSubjectOrderByCreatedAtAsc(subject);
        var proposals = new ArrayList<ImportDtos.Proposal>();
        DICTIONARY.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            if (!containsPhrase(text, entry.getKey())) return;
            var title = Arrays.stream(entry.getKey().split(" ")).map(word -> Character.toUpperCase(word.charAt(0)) + word.substring(1)).collect(java.util.stream.Collectors.joining(" "));
            var duplicate = existing.stream().anyMatch(node -> node.getType() == entry.getValue() && canonical(node.getTitle()).equals(canonical(entry.getKey())));
            var evidence = sourceExcerpt(rawText, entry.getKey(), 240);
            var id = UUID.nameUUIDFromBytes((entry.getValue().name() + ":" + entry.getKey()).getBytes(StandardCharsets.UTF_8)).toString();
            proposals.add(new ImportDtos.Proposal(id, entry.getValue(), title,
                    entry.getValue() == NodeType.SKILL ? SkillLevel.LEARNING : null, evidence,
                    "DETERMINISTIC_MATCH", "Confirm the type and level; text alone cannot prove proficiency.", duplicate));
        });
        Arrays.stream(rawText.split("\\R", 5001)).limit(5000)
                .map(String::trim)
                .filter(line -> line.matches("(?i)^(?:[-*]\\s*)?(?:project|built|created|developed)\\s*[:\\-]\\s*.{3,180}$"))
                .map(line -> line.replaceFirst("(?i)^(?:[-*]\\s*)?(?:project|built|created|developed)\\s*[:\\-]\\s*", "").trim())
                .map(title -> title.length() > 180 ? title.substring(0, 180).trim() : title)
                .filter(title -> !title.isBlank())
                .distinct()
                .limit(8)
                .forEach(title -> {
                    var duplicate = existing.stream().anyMatch(node -> node.getType() == NodeType.PROJECT && canonical(node.getTitle()).equals(canonical(title)));
                    var id = UUID.nameUUIDFromBytes((NodeType.PROJECT.name() + ":" + canonical(title)).getBytes(StandardCharsets.UTF_8)).toString();
                    proposals.add(new ImportDtos.Proposal(id, NodeType.PROJECT, title, null,
                            sourceExcerpt(rawText, title, 240), "DETERMINISTIC_MATCH",
                            "Confirm that this line describes your own project and edit the title if needed.", duplicate));
                });
        addAiProposals(subject, rawText, existing, proposals);
        return proposals.stream().collect(java.util.stream.Collectors.toMap(
                proposal -> proposal.type().name() + ":" + canonical(proposal.title()),
                proposal -> proposal, (first, ignored) -> first, LinkedHashMap::new))
                .values().stream().limit(40).toList();
    }

    private void addAiProposals(String subject, String rawText, List<GraphNode> existing, List<ImportDtos.Proposal> proposals) {
        if (!aiProvider.supportsImportExtraction() || !aiCostGate.mayCall(subject, aiProvider)) return;
        var boundedText = rawText.substring(0, Math.min(rawText.length(), aiPolicy.maxInputCharacters()));
        var request = new AiProvider.ImportRequest(boundedText, 20, 240);
        AiProvider.ImportResponse response = null;
        try {
            response = aiExecutor.extractImport(aiProvider, request).orElseThrow(() -> new IllegalArgumentException("AI import extraction is unsupported"));
            AiResponseValidator.validateImport(response, request).forEach(candidate -> {
                        var type = NodeType.valueOf(candidate.type());
                        var level = candidate.suggestedLevel() == null ? null : SkillLevel.valueOf(candidate.suggestedLevel());
                        var duplicate = existing.stream().anyMatch(node -> node.getType() == type && canonical(node.getTitle()).equals(canonical(candidate.title())));
                        var id = UUID.nameUUIDFromBytes((type.name() + ":" + canonical(candidate.title())).getBytes(StandardCharsets.UTF_8)).toString();
                        proposals.add(new ImportDtos.Proposal(id, type, candidate.title(), level,
                                candidate.evidenceExcerpt(), "AI_SUGGESTION_UNVERIFIED", candidate.uncertainty(), duplicate));
                    });
            recordAiImport(subject, boundedText, response, null);
        } catch (RuntimeException problem) {
            var code = problem instanceof AiProviderExecutor.AiProviderFailure failure ? failure.code() : "AI_IMPORT_OUTPUT_REJECTED";
            recordAiImport(subject, boundedText, response, code);
            // Imported text and model output are untrusted. Deterministic proposals remain available.
        }
    }

    private void recordAiImport(String subject, String boundedText, AiProvider.ImportResponse response, String errorCode) {
        try {
            var ids = response == null || response.proposals() == null ? List.of() : response.proposals().stream()
                    .map(candidate -> candidate.type() + ":" + canonical(candidate.title())).toList();
            aiOperations.save(new AiOperation(UUID.randomUUID(), users.require(subject), null, "IMPORT_EXTRACTION",
                    sha256(boundedText), aiProvider.providerId(), aiProvider.modelId(),
                    errorCode == null ? AiOperation.Status.SUCCEEDED : AiOperation.Status.FAILED,
                    response == null ? null : response.inputTokens(), response == null ? null : response.outputTokens(),
                    response == null ? null : response.latencyMs(),
                    response == null || response.estimatedCostUsd() == null ? java.math.BigDecimal.ZERO : response.estimatedCostUsd(),
                    java.math.BigDecimal.valueOf(aiPolicy.maxEstimatedCostPerCallUsd()),
                    objectMapper.writeValueAsString(ids), errorCode));
        } catch (Exception ignored) {
            // Telemetry must never retain raw uploads or prevent deterministic review.
        }
    }

    private String sourceExcerpt(String rawText, String phrase, int maxLength) {
        var normalized = rawText.replaceAll("\\s+", " ").trim();
        var index = normalized.toLowerCase(Locale.ROOT).indexOf(phrase.toLowerCase(Locale.ROOT));
        if (index < 0) return normalized.substring(0, Math.min(maxLength, normalized.length()));
        var start = Math.max(0, index - 70);
        var end = Math.min(normalized.length(), Math.max(index + phrase.length() + 70, start + Math.min(maxLength, normalized.length() - start)));
        if (end - start > maxLength) end = start + maxLength;
        return normalized.substring(start, end).trim();
    }

    private boolean containsPhrase(String text, String phrase) { return (" " + text + " ").contains(" " + phrase + " "); }
    private boolean stronger(SkillLevel candidate, SkillLevel existing) { return candidate != null && (existing == null || candidate.ordinal() > existing.ordinal()); }
    private String canonical(String value) {
        var normalized = value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
        return switch (normalized) {
            case "spring" -> "spring boot";
            case "ai" -> "artificial intelligence";
            case "js" -> "javascript";
            default -> normalized;
        };
    }
    private String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception problem) { throw new IllegalStateException("Unable to fingerprint import input", problem); }
    }
    private ImportJob requireJob(String subject, UUID id) { return jobs.findByIdAndOwnerSubject(id, subject).orElseThrow(() -> ApiException.notFound("import")); }
    private List<ImportDtos.Proposal> readProposals(String json) {
        try { return objectMapper.readValue(json, new TypeReference<>() {}); }
        catch (Exception problem) { throw new IllegalStateException("Stored import proposals are invalid", problem); }
    }
    private ImportDtos.Job response(ImportJob job) { return new ImportDtos.Job(job.getId(), job.getSourceType(), job.getStatus(), readProposals(job.getProposedItems()), job.getErrorCode(), job.getCreatedAt(), job.getUpdatedAt(), job.getAcceptedAt()); }
}
