package com.ssafy.snaphere.domain.bookmark.service;

import com.ssafy.snaphere.domain.bookmark.repository.BookmarkRepository;
import com.ssafy.snaphere.domain.place.repository.PlaceRepository;
import com.ssafy.snaphere.domain.place.repository.PlaceWriteRepository;
import com.ssafy.snaphere.domain.post.repository.PostRepository;
import com.ssafy.snaphere.domain.post.repository.PostWriteRepository;
import com.ssafy.snaphere.global.error.BusinessException;
import com.ssafy.snaphere.global.error.ErrorCode;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BookmarkService {

    private static final String POST = "POST";
    private static final String PLACE = "PLACE";

    private final BookmarkRepository bookmarkRepository;
    private final PostRepository postRepository;
    private final PostWriteRepository postWriteRepository;
    private final PlaceRepository placeRepository;
    private final PlaceWriteRepository placeWriteRepository;

    @Transactional
    public Result toggle(Long userId, String targetType, Long targetId, boolean on) {
        String type = normalize(targetType);
        // FK 가 없으므로 존재 여부를 여기서 확인한다. 안 하면 삭제된 대상이 저장 목록에 남는다.
        requireTargetExists(type, targetId);

        boolean changed = on
                ? bookmarkRepository.add(userId, type, targetId)
                : bookmarkRepository.remove(userId, type, targetId);

        if (changed) {
            int delta = on ? 1 : -1;
            if (POST.equals(type)) postWriteRepository.addBookmarkCount(targetId, delta);
            else placeWriteRepository.addBookmarkCount(targetId, delta);
        }
        return new Result(type, targetId, on);
    }

    @Transactional(readOnly = true)
    public List<Long> listPostIds(Long userId, int page, int size) {
        return bookmarkRepository.findTargetIds(userId, POST, size, (page - 1) * size);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listPlaces(Long userId, int page, int size) {
        return bookmarkRepository.findBookmarkedPlaces(userId, size, (page - 1) * size);
    }

    @Transactional(readOnly = true)
    public boolean isBookmarked(Long userId, String targetType, Long targetId) {
        if (userId == null) return false;
        return bookmarkRepository.exists(userId, normalize(targetType), targetId);
    }

    private void requireTargetExists(String type, Long targetId) {
        boolean exists = POST.equals(type)
                ? postRepository.findById(targetId).filter(p -> p.isActive()).isPresent()
                : placeRepository.findById(targetId).filter(p -> p.isActive()).isPresent();
        if (!exists) {
            throw new BusinessException(POST.equals(type) ? ErrorCode.POST_002 : ErrorCode.PLACE_001);
        }
    }

    private static String normalize(String raw) {
        if (raw == null) throw new BusinessException(ErrorCode.COMMON_400, "targetType");
        String t = raw.trim().toUpperCase();
        if (!POST.equals(t) && !PLACE.equals(t)) {
            throw new BusinessException(ErrorCode.COMMON_400, "targetType");
        }
        return t;
    }

    public record Result(String targetType, Long targetId, boolean bookmarked) {}
}
