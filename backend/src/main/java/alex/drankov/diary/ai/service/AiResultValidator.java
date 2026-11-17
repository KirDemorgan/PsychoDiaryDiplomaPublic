package alex.drankov.diary.ai.service;

import alex.drankov.diary.ai.dto.CandidateResponse;
import alex.drankov.diary.ai.dto.ModelExtraction;
import alex.drankov.diary.ai.dto.ModelNarrative;
import alex.drankov.diary.reports.dto.FactResponse;
import alex.drankov.diary.reports.dto.OverviewClaimResponse;
import alex.drankov.diary.reports.dto.OverviewProvenanceResponse;
import alex.drankov.diary.reports.dto.ReportOverviewResponse;
import alex.drankov.diary.reports.dto.SourceRefResponse;
import alex.drankov.diary.reports.entity.OverviewMode;
import alex.drankov.diary.reports.service.TemplateOverview;
import alex.drankov.diary.tags.dto.ManualTagRequest;
import alex.drankov.diary.tags.service.TagService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.cfg.EnumFeature;
import tools.jackson.databind.json.JsonMapper;

@Component
@RequiredArgsConstructor
public class AiResultValidator {
    private static final int MAX_CLAIMS = 8;
    private static final int MAX_FACTS_PER_CLAIM = 8;
    private static final int MAX_CLAIM_LENGTH = 600;
    private static final int MAX_TOTAL_LENGTH = 3000;

    private final JsonMapper json;
    private final TemplateOverview template;

    public List<CandidateResponse> extraction(
            String text,
            String raw
    ) {
        ModelExtraction result = json.readerFor(ModelExtraction.class)
                .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .with(EnumFeature.FAIL_ON_NUMBERS_FOR_ENUMS)
                .with(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .without(DeserializationFeature.ACCEPT_FLOAT_AS_INT).readValue(bounded(raw));
        if (result.candidates() == null || result.candidates().size() > 100) {
            throw new IllegalArgumentException("Invalid candidate list");
        }
        var source = json.readTree(raw).get("candidates");
        int index = 0;
        var candidates = new ArrayList<CandidateResponse>();
        var unique = new HashSet<String>();
        for (var candidate : result.candidates()) {
            if (candidate == null || candidate.quote() == null || candidate.quote().isBlank()
                    || candidate.labelCode() == null || candidate.presence() == null || candidate.temporal() == null || candidate.subject() == null) {
                throw new IllegalArgumentException("Incomplete candidate");
            }
            var node = source.get(index++);
            if (!node.get("quote").isString() || node.hasNonNull("occurrence") && !node.get("occurrence").isIntegralNumber()) {
                throw new IllegalArgumentException("Invalid JSON field type");
            }
            int occurrence = candidate.occurrence() == null ? 0 : candidate.occurrence();
            if (occurrence < 0 || occurrence > 5000) {
                throw new IllegalArgumentException("Invalid occurrence");
            }
            int start = text.indexOf(candidate.quote());
            if (start < 0) {
                throw new IllegalArgumentException("Unverified quotation");
            }
            boolean repeated = text.indexOf(candidate.quote(), start + 1) >= 0;
            if (repeated) {
                start = -1;
                for (int i = 0; i <= occurrence; i++) {
                    start = text.indexOf(candidate.quote(), start + 1);
                    if (start < 0) {
                        throw new IllegalArgumentException("Unverified quotation");
                    }
                }
                if (candidate.occurrence() == null && text.indexOf(candidate.quote(), start + 1) >= 0) {
                    throw new IllegalArgumentException("Ambiguous quotation");
                }
            }
            int end = start + candidate.quote().length();
            TagService.validate(text, new ManualTagRequest(candidate.labelCode(), candidate.quote(), start, end,
                    candidate.presence(), candidate.temporal(), candidate.subject(), null));
            if (!unique.add(candidate.labelCode() + ":" + start + ":" + end)) {
                throw new IllegalArgumentException("Duplicate candidate");
            }
            candidates.add(new CandidateResponse(UUID.randomUUID(), candidate.labelCode(), candidate.labelCode().getCategory(),
                    candidate.quote(), start, end, candidate.presence(), candidate.temporal(), candidate.subject()));
        }
        return List.copyOf(candidates);
    }

    /**
     * Validates a free-text overview: structure, existing fact references, and the {@link NarrativeGuard} checks.
     *
     * <p>The model cites facts by short aliases ({@link AiQueue#alias}); long hashes were copied unreliably.
     * Source references are derived from the cited facts, never taken from the model. Every claim must cite at
     * least one calculated fact, so no statement is published without a verifiable basis.
     */
    public ReportOverviewResponse narrative(
            String raw,
            List<FactResponse> facts,
            String modelId,
            String prompt,
            String schema
    ) {
        ModelNarrative result = json.readerFor(ModelNarrative.class)
                .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .with(StreamReadFeature.STRICT_DUPLICATE_DETECTION).readValue(bounded(raw));
        if (result.claims() == null || result.claims().isEmpty() || result.claims().size() > MAX_CLAIMS) {
            throw new IllegalArgumentException("Invalid overview list");
        }
        Map<String, FactResponse> allowed = new HashMap<>();
        for (int i = 0; i < facts.size(); i++) {
            allowed.put(AiQueue.alias(i), facts.get(i));
        }
        var claims = new ArrayList<OverviewClaimResponse>();
        var texts = new HashSet<String>();
        int total = 0;
        for (var claim : result.claims()) {
            if (claim == null || claim.text() == null || claim.factIds() == null || claim.factIds().isEmpty()
                    || claim.factIds().size() > MAX_FACTS_PER_CLAIM
                    || claim.factIds().stream().anyMatch(id -> id == null || !allowed.containsKey(id))
                    || new HashSet<>(claim.factIds()).size() != claim.factIds().size()) {
                throw new IllegalArgumentException("Unsupported claim");
            }
            String text = claim.text().strip();
            total += text.length();
            if (text.isEmpty() || text.length() > MAX_CLAIM_LENGTH || total > MAX_TOTAL_LENGTH || !texts.add(text)) {
                throw new IllegalArgumentException("Invalid claim text");
            }
            var cited = claim.factIds().stream().map(allowed::get).toList();
            NarrativeGuard.verify(text, cited, template::sentence);
            List<SourceRefResponse> refs = cited.stream().flatMap(fact -> fact.sourceRefs().stream()).distinct().toList();
            claims.add(new OverviewClaimResponse("ai-" + claims.size(), text,
                    cited.stream().map(FactResponse::factId).toList(), refs));
        }
        return new ReportOverviewResponse("AI-обзор наблюдений для обсуждения с врачом", List.copyOf(claims),
                List.of("Текст сформулирован нейросетью по рассчитанным фактам; числа и даты проверены сервером",
                        "Обзор не является медицинским заключением; связи не доказывают причинность",
                        "Числа и даты рассчитаны сервером"), new OverviewProvenanceResponse(OverviewMode.AI, modelId, prompt, schema));
    }

    private String bounded(String raw) {
        if (raw == null || raw.length() > 65536) {
            throw new IllegalArgumentException("Invalid result size");
        }
        return raw;
    }
}
