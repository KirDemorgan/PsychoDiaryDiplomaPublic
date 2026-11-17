package alex.drankov.diary.ai.entity;

import alex.drankov.diary.ai.dto.CandidateResponse;
import alex.drankov.diary.common.persistence.BaseEntity;
import alex.drankov.diary.tags.entity.TagCode;
import alex.drankov.diary.tags.entity.TagPresence;
import alex.drankov.diary.tags.entity.TagSubject;
import alex.drankov.diary.tags.entity.TagTemporal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.util.UUID;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "analysis_candidate")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AnalysisCandidate extends BaseEntity {
    @Column(nullable = false)
    private UUID taskId;

    @Column(nullable = false)
    private UUID entryId;

    @Column(nullable = false)
    private int revision;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TagCode labelCode;

    @Column(nullable = false, columnDefinition = "text")
    private String quote;

    @Column(nullable = false)
    private int startIndex;

    @Column(nullable = false)
    private int endIndex;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TagPresence presence;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TagTemporal temporal;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TagSubject subject;

    public AnalysisCandidate(
            AiTask task,
            CandidateResponse candidate
    ) {
        taskId = task.getId();
        entryId = task.getEntryId();
        revision = task.getRevision();
        labelCode = candidate.labelCode();
        quote = candidate.quote();
        startIndex = candidate.start();
        endIndex = candidate.end();
        presence = candidate.presence();
        temporal = candidate.temporal();
        subject = candidate.subject();
    }

    public CandidateResponse response() {
        return new CandidateResponse(getId(), labelCode, labelCode.getCategory(), quote, startIndex, endIndex,
                presence, temporal, subject);
    }
}
