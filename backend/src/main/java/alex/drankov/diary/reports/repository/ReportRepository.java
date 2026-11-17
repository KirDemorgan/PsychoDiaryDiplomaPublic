package alex.drankov.diary.reports.repository;

import alex.drankov.diary.reports.entity.Report;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportRepository extends JpaRepository<Report, UUID> {
    Optional<Report> findByIdAndUserId(UUID id, UUID userId);

    Page<Report> findByUserId(UUID userId, Pageable page);

    List<Report> findByUserIdOrderByCreatedAtAscIdAsc(UUID userId);
}
