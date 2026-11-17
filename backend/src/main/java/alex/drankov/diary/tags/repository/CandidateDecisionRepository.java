package alex.drankov.diary.tags.repository;

import alex.drankov.diary.tags.entity.CandidateDecision;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CandidateDecisionRepository extends JpaRepository<CandidateDecision, UUID> {
}
