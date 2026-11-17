package alex.drankov.diary.entries.repository;

import alex.drankov.diary.entries.entity.EntryRevision;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EntryRevisionRepository extends JpaRepository<EntryRevision, UUID> {
}
