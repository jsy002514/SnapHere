package com.snaphere.api.post;

import com.snaphere.api.post.entity.TagEntity;
import com.snaphere.api.post.repository.TagRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 해시태그 마스터를 이름으로 찾거나 만든다. (CMU-025)
 *
 * <p>정규화는 {@link TagEntity#normalize(String)} 한 곳에만 둔다. 저장할 때와 검색할 때 규칙이
 * 갈리면 같은 태그가 두 행으로 갈라진다.
 */
@Service
public class TagService {

    private final TagRepository tags;

    public TagService(TagRepository tags) {
        this.tags = tags;
    }

    /**
     * 입력 순서를 지키면서 태그를 찾거나 만든다.
     *
     * <p>정규화 결과가 같은 이름은 하나로 합친다 — {@code #서울}, {@code 서울}, {@code 서 울} 은
     * 한 태그다. 중복을 제거한 뒤의 개수가 실제 태그 수이므로 개수 검증은 이 결과로 해야 한다.
     */
    @Transactional
    public List<TagEntity> resolveAll(List<String> rawNames) {
        Map<String, String> byNormalized = new LinkedHashMap<>();
        for (String raw : rawNames) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String normalized = TagEntity.normalize(raw);
            if (normalized.isEmpty()) {
                continue;
            }
            byNormalized.putIfAbsent(normalized, raw);
        }
        if (byNormalized.isEmpty()) {
            return List.of();
        }

        Map<String, TagEntity> found = new LinkedHashMap<>();
        for (TagEntity tag : tags.findByNormalizedNameIn(new LinkedHashSet<>(byNormalized.keySet()))) {
            found.put(tag.getNormalizedName(), tag);
        }

        List<TagEntity> result = new ArrayList<>(byNormalized.size());
        for (Map.Entry<String, String> entry : byNormalized.entrySet()) {
            TagEntity tag = found.get(entry.getKey());
            result.add(tag != null ? tag : create(entry.getValue(), entry.getKey()));
        }
        return result;
    }

    /**
     * 없으면 만든다. 동시에 같은 태그를 처음 쓰는 요청이 둘이면 유니크 제약이 하나를 튕기므로,
     * 그때는 상대가 만든 행을 다시 읽는다 — 조회 후 삽입만으로는 이 경합을 막을 수 없다.
     */
    private TagEntity create(String rawName, String normalized) {
        try {
            return tags.saveAndFlush(TagEntity.of(rawName));
        } catch (DataIntegrityViolationException duplicated) {
            return tags.findByNormalizedName(normalized).orElseThrow(() -> duplicated);
        }
    }
}
