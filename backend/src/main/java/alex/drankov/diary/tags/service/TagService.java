package alex.drankov.diary.tags.service;

import alex.drankov.diary.ai.service.AnalysisQuery;
import alex.drankov.diary.ai.service.CandidateReview;
import alex.drankov.diary.common.api.ApiException;
import alex.drankov.diary.common.api.Versions;
import alex.drankov.diary.common.idempotency.dto.MutationResult;
import alex.drankov.diary.common.idempotency.service.Idempotency;
import alex.drankov.diary.entries.dto.EntryResponse;
import alex.drankov.diary.entries.mapper.EntryMapper;
import alex.drankov.diary.entries.repository.EntryRepository;
import alex.drankov.diary.tags.dto.ManualTagRequest;
import alex.drankov.diary.tags.dto.TagDefinitionResponse;
import alex.drankov.diary.tags.dto.TagDefinitionsResponse;
import alex.drankov.diary.tags.dto.TagReviewRequest;
import alex.drankov.diary.tags.entity.TagCode;
import alex.drankov.diary.tags.entity.TagReview;
import alex.drankov.diary.tags.repository.EntryTagRepository;
import alex.drankov.diary.tags.repository.TagReviewRepository;
import alex.drankov.diary.users.service.UserAccess;

import java.util.Arrays;
import java.util.UUID;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TagService {
    private final EntryRepository entries;
    private final TagReviewRepository reviews;
    private final EntryTagRepository tags;
    private final EntryMapper mapper;
    private final TagQuery query;
    private final UserAccess access;
    private final Idempotency idempotency;
    private final CandidateReview candidates;
    private final AnalysisQuery analyses;

    public TagDefinitionsResponse definitions() {
        return new TagDefinitionsResponse("1", Arrays.stream(TagCode.values())
                .map(code -> new TagDefinitionResponse(code, code.getLabelRu(), code.getCategory())).toList());
    }

    @Transactional
    public MutationResult<EntryResponse> review(
            UUID user,
            UUID id,
            String ifMatch,
            UUID key,
            TagReviewRequest request
    ) {
        var owner = access.lock(user);
        return idempotency.execute(user, "PUT /entries/" + id + "/tag-review", key, request, EntryResponse.class, () -> {
            var entry = entries.findByIdAndUserId(id, user).orElseThrow(ApiException::notFound);
            Versions.require(ifMatch, entry.getVersion());
            if (request.revision() == null || request.revision() != entry.getRevision()) {
                throw new ApiException(409, "STALE_REVISION", "Редакция записи изменилась");
            }
            if (request.complete() == null || request.decisions() == null || request.manualTags() == null
                    || request.manualTags().size() > 100) {
                throw ApiException.invalid("Укажите complete, decisions и до 100 ручных тегов");
            }
            var review = new TagReview(id, entry.getRevision(), request.complete());
            var prepared = candidates.prepare(entry, review, request);
            reviews.saveAndFlush(review);
            candidates.saveDecisions(prepared);
            tags.saveAllAndFlush(prepared.tags());
            entry.review(review.getId(), request.complete());
            entries.flush();
            owner.advanceDataRevision();
            return new MutationResult<>(mapper.response(entry, query.current(entry), analyses.latest(entry)), id, entry.getVersion(), 200);
        });
    }

    public static void validate(
            String text,
            ManualTagRequest tag
    ) {
        if (tag == null || tag.labelCode() == null || tag.presence() == null || tag.temporal() == null
                || tag.subject() == null || tag.quote() == null || tag.quote().isBlank()
                || tag.start() == null || tag.end() == null || tag.start() < 0 || tag.end() <= tag.start()
                || tag.end() > text.length() || splitSurrogate(text, tag.start()) || splitSurrogate(text, tag.end())
                || !text.substring(tag.start(), tag.end()).equals(tag.quote())) {
            throw ApiException.invalid("Тег должен содержать точную цитату и корректные границы UTF-16 текущего текста");
        }
    }

    private static boolean splitSurrogate(
            String text,
            int position
    ) {
        return position > 0 && position < text.length() && Character.isHighSurrogate(text.charAt(position - 1))
                && Character.isLowSurrogate(text.charAt(position));
    }
}
