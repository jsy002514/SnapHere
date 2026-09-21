package com.snaphere.api.post.media;

import com.snaphere.api.media.storage.InMemoryMediaObjectStore;
import com.snaphere.api.media.storage.MediaObjectKeys;
import com.snaphere.api.media.storage.MediaStorageProperties;
import com.snaphere.api.media.storage.MediaUrlResolver;
import com.snaphere.api.post.entity.PostImageEntity;
import com.snaphere.api.post.repository.PostImageRepository;
import com.snaphere.api.post.repository.PostRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 사진 후처리 흐름 — PST-019, PST-020
 *
 * <p>저장소는 실제 스텁 구현을 쓴다. 어떤 키에 무엇이 올라갔는지가 이 클래스의 책임이라
 * 그 부분을 흉내로 대체하면 검증할 것이 남지 않는다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PostImagePostProcessorTest {

    private static final long POST_ID = 42L;
    private static final String KEY = "originals/posts/11111111-2222-3333-4444-555555555555/abc.webp";

    @Mock private PostImageRepository postImages;
    @Mock private PostRepository posts;
    @Mock private MediaProcessingStateStore states;

    private InMemoryMediaObjectStore store;
    private PostImagePostProcessor processor;

    @BeforeEach
    void setUp() {
        store = new InMemoryMediaObjectStore();
        MediaUrlResolver urls = new MediaUrlResolver(new MediaStorageProperties(
                "stub", "b", "ap-northeast-2", "https://cdn.test", Duration.ofMinutes(5), 1));
        processor = new PostImagePostProcessor(postImages, store, urls, posts, states);
        when(states.begin(POST_ID)).thenReturn(1);
        when(postImages.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
    }

    private PostImageEntity newImage() {
        return PostImageEntity.create(POST_ID, KEY, 1, null, null);
    }

    @Test
    @DisplayName("원본은 originals/ 에 좌표까지 그대로 남는다")
    void 원본_보관() throws IOException {
        byte[] original = JpegFixtures.jpegWithExif(800, 600);
        store.put(KEY, original, "image/jpeg");
        when(postImages.findByPostIdOrderBySortOrder(POST_ID)).thenReturn(List.of(newImage()));

        processor.process(POST_ID);

        assertThat(store.get(MediaObjectKeys.original(KEY))).isPresent();
        assertThat(store.get(MediaObjectKeys.original(KEY)).get()).isEqualTo(original);
        assertThat(JpegFixtures.contains(store.get(MediaObjectKeys.original(KEY)).get(),
                JpegFixtures.FAKE_GPS)).isTrue();
    }

    @Test
    @DisplayName("비공개 원본과 분리된 공개 키에 EXIF 없는 이미지를 쓴다")
    void 공개키_덮어쓰기() throws IOException {
        byte[] original = JpegFixtures.jpegWithExif(800, 600);
        store.put(KEY, original, "image/jpeg");
        when(postImages.findByPostIdOrderBySortOrder(POST_ID)).thenReturn(List.of(newImage()));

        processor.process(POST_ID);

        String publicKey=MediaObjectKeys.publicImage(KEY);
        assertThat(store.get(publicKey)).isPresent();
        byte[] published = store.get(publicKey).get();
        assertThat(JpegFixtures.contains(published, JpegFixtures.EXIF_MARKER)).isFalse();
        assertThat(JpegFixtures.contains(published, JpegFixtures.FAKE_GPS)).isFalse();
        // 단색 이미지는 재인코딩해도 픽셀 바이트가 같을 수 있다. 이 커밋이 보장하는 것은
        // "바이트가 달라진다"가 아니라 "메타데이터가 사라진다"다.
        assertThat(ImageIO.read(new ByteArrayInputStream(published))).isNotNull();
    }

    @Test
    @DisplayName("썸네일을 thumbs/ 에 올리고 그 주소를 행에 채운다")
    void 썸네일_생성() throws IOException {
        store.put(KEY, JpegFixtures.jpeg(1600, 1200), "image/jpeg");
        PostImageEntity image = newImage();
        when(postImages.findByPostIdOrderBySortOrder(POST_ID)).thenReturn(List.of(image));

        processor.process(POST_ID);

        assertThat(store.get(MediaObjectKeys.thumbnail(KEY))).isPresent();
        assertThat(image.getThumbnailUrl())
                .isEqualTo("https://cdn.test/" + MediaObjectKeys.thumbnail(KEY));
    }

    @Test
    @DisplayName("해시와 실제 비율을 행에 채운다")
    void 해시와_비율_채움() throws IOException {
        byte[] original = JpegFixtures.jpeg(1200, 800);
        store.put(KEY, original, "image/jpeg");
        PostImageEntity image = newImage();
        when(postImages.findByPostIdOrderBySortOrder(POST_ID)).thenReturn(List.of(image));

        processor.process(POST_ID);

        assertThat(image.getImageHash()).isEqualTo(ImagePostProcessing.sha256(original));
        assertThat(image.getAspectRatio()).isEqualByComparingTo("1.5000");
        verify(postImages).saveAndFlush(image);
    }

    @Test
    @DisplayName("업로드되지 않은 사진은 건너뛴다 — 게시글은 이미 만들어졌다")
    void 원본_없으면_건너뛴다() {
        when(postImages.findByPostIdOrderBySortOrder(POST_ID)).thenReturn(List.of(newImage()));

        processor.process(POST_ID);

        assertThat(store.get(MediaObjectKeys.thumbnail(KEY))).isEmpty();
        verify(postImages, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("이미 처리된 사진은 다시 처리하지 않는다 — 재인코딩이 반복되면 화질만 떨어진다")
    void 재실행_안전() throws IOException {
        store.put(KEY, JpegFixtures.jpeg(800, 600), "image/jpeg");
        PostImageEntity done = newImage();
        done.completePostProcessing(MediaObjectKeys.publicImage(KEY),
                "https://cdn.test/public/thumbs/x.jpg", "b".repeat(64), null);
        when(postImages.findByPostIdOrderBySortOrder(POST_ID)).thenReturn(List.of(done));

        processor.process(POST_ID);

        assertThat(store.get(MediaObjectKeys.original(KEY))).isPresent();
        verify(postImages, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("사진이 없는 게시글에서도 예외를 던지지 않는다")
    void 사진_없는_게시글() {
        when(postImages.findByPostIdOrderBySortOrder(POST_ID)).thenReturn(List.of());
        processor.process(POST_ID);
        verify(postImages, never()).saveAndFlush(any());
    }
}
