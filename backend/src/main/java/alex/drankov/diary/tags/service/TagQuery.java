package alex.drankov.diary.tags.service;

import alex.drankov.diary.entries.entity.DiaryEntry;
import alex.drankov.diary.tags.dto.TagResponse;
import alex.drankov.diary.tags.mapper.TagMapper;
import alex.drankov.diary.tags.repository.EntryTagRepository;

import java.util.List;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TagQuery {
    private final EntryTagRepository tags;
    private final TagMapper mapper;

    public List<TagResponse> current(DiaryEntry entry) {
        return entry.getCurrentReviewId() == null ? List.of()
                : tags.findByReviewIdOrderByStartAscIdAsc(entry.getCurrentReviewId()).stream().map(mapper::response).toList();
    }

    public List<String> observedCodes(DiaryEntry entry) {
        return entry.getCurrentReviewId() == null ? List.of()
                : tags.findByReviewIdOrderByStartAscIdAsc(entry.getCurrentReviewId()).stream()
                .filter(tag -> tag.observed()).map(tag -> tag.getLabelCode().name()).distinct().sorted().toList();
    }
}
