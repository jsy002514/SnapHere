package com.snaphere.api.post.media;

import com.snaphere.api.media.storage.MediaObjectKeys;
import com.snaphere.api.media.storage.MediaObjectStore;
import com.snaphere.api.media.storage.MediaUrlResolver;
import com.snaphere.api.post.entity.PostImageEntity;
import com.snaphere.api.post.repository.PostImageRepository;
import com.snaphere.api.post.repository.PostRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * 게시글 사진 후처리. (PST-019, PST-020, PST-021, PST-031)
 *
 * <p>사진 한 장마다 이렇게 한다.
 * <ol>
 *   <li>원본을 {@code originals/} 로 복사해 좌표가 남은 사본을 보관한다 (PST-020)</li>
 *   <li>픽셀만 다시 인코딩해 EXIF·GPS·기기 정보를 떨어뜨리고 공개 키에 덮어쓴다 (PST-020)</li>
 *   <li>썸네일을 만들어 {@code thumbs/} 에 올린다</li>
 *   <li>원본 해시와 실제 비율을 {@code post_images} 에 채운다 (PST-021, PST-031)</li>
 * </ol>
 *
 * <p><b>한 장이 실패해도 나머지는 계속한다.</b> 사진 네 장 중 하나가 깨졌다고 나머지 세 장이
 * 썸네일 없이 남으면 목록이 더 이상해진다. 실패는 로그로 남기고 다음 장으로 간다.
 *
 * <p><b>다시 돌려도 안전하다.</b> 이미 처리된 사진(해시가 채워진 사진)은 건너뛴다. 원본 복사는
 * 같은 내용으로 덮어쓰기이고, 덮어쓴 공개 이미지를 다시 인코딩하면 화질만 조금 더 떨어진다 —
 * 그래서 건너뛰기 조건이 필요하다.
 */
@Component
public class PostImagePostProcessor {

    private static final Logger log = LoggerFactory.getLogger(PostImagePostProcessor.class);

    private final PostImageRepository postImages;
    private final MediaObjectStore objectStore;
    private final MediaUrlResolver urlResolver;
    private final PostRepository posts;
    private final MediaProcessingStateStore states;

    public PostImagePostProcessor(PostImageRepository postImages,
                                  MediaObjectStore objectStore,
                                  MediaUrlResolver urlResolver,
                                  PostRepository posts,
                                  MediaProcessingStateStore states) {
        this.postImages = postImages;
        this.objectStore = objectStore;
        this.urlResolver = urlResolver;
        this.posts = posts;
        this.states = states;
    }

    public void process(long postId) {
        int attempt=states.begin(postId);
        if (attempt <= 0) return;
        List<PostImageEntity> images = postImages.findByPostIdOrderBySortOrder(postId);
        if (images.isEmpty()) {
            log.warn("후처리할 사진이 없다. postId={}", postId);
            states.complete(postId,false,attempt);
            return;
        }
        for (PostImageEntity image : images) {
            processOne(postId, image);
        }
        boolean ready=postImages.isPostReady(postId);
        if (ready) posts.findById(postId).ifPresent(post -> {
            post.completeMediaProcessing(); posts.save(post);
        });
        states.complete(postId,ready,attempt);
    }

    private void processOne(long postId, PostImageEntity image) {
        if (image.getImageHash() != null && image.getThumbnailUrl() != null) {
            return; // 이미 처리됐다
        }
        String key = image.getImageKey();

        Optional<byte[]> original = objectStore.get(key);
        if (original.isEmpty()) {
            // 서명 주소를 받고 실제 업로드를 하지 않은 경우다. 게시글은 이미 만들어졌으므로
            // 여기서 예외를 던져 봐야 되돌릴 것이 없다 (PST-013).
            log.warn("원본이 없어 후처리를 건너뛴다. postId={} imageKey={}", postId, key);
            return;
        }

        try {
            ProcessedImage processed = ImagePostProcessing.process(original.get());

            String originalKey=MediaObjectKeys.original(key);
            if (!MediaObjectKeys.isPrivateOriginal(key)) objectStore.copy(key,originalKey);
            String publicKey=MediaObjectKeys.publicImage(originalKey);
            objectStore.put(publicKey, processed.sanitized(), processed.contentType());

            String thumbnailKey = MediaObjectKeys.thumbnail(originalKey);
            objectStore.put(thumbnailKey, processed.thumbnail(), processed.contentType());

            image.completePostProcessing(publicKey,urlResolver.publicUrl(thumbnailKey),
                    processed.sha256(), processed.aspectRatio());
            postImages.saveAndFlush(image);

            log.debug("후처리 완료. postId={} imageKey={} ratio={} hash={}",
                    postId, key, processed.aspectRatio(), processed.sha256());

        } catch (DataIntegrityViolationException duplicated) {
            // (post_id, image_hash) 유니크 위반 — 같은 게시글에 같은 사진을 두 번 넣었다.
            // 등록 시점의 사전 검사(PST-031)는 클라이언트가 보낸 해시로만 하므로 여기서 처음
            // 드러날 수 있다. 게시글을 되돌리지는 않고 해시 없이 남긴다.
            log.warn("같은 게시글에 중복 이미지가 있어 해시를 채우지 못했다. postId={} imageKey={} (PST-031)",
                    postId, key);
        } catch (IOException | RuntimeException failure) {
            log.error("후처리 실패. 이 사진만 건너뛴다. postId={} imageKey={}", postId, key, failure);
        }
    }
}
