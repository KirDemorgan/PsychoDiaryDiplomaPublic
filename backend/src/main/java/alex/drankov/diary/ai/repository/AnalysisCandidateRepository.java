package alex.drankov.diary.ai.repository;

import alex.drankov.diary.ai.entity.AnalysisCandidate;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalysisCandidateRepository extends JpaRepository<AnalysisCandidate, UUID> {
    List<AnalysisCandidate> findByTaskIdOrderByStartIndexAscIdAsc(UUID task);
}
