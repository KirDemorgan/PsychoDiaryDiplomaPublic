package alex.drankov.diary.tags.repository;

import alex.drankov.diary.tags.entity.TagReview;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TagReviewRepository extends JpaRepository<TagReview, UUID> {
}
