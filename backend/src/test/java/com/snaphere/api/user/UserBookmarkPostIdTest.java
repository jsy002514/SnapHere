package com.snaphere.api.user;

import com.snaphere.api.auth.User;
import com.snaphere.api.auth.UserRepository;
import com.snaphere.api.auth.UserStatus;
import com.snaphere.api.common.web.PagingProperties;
import com.snaphere.api.post.PostResponseAssembler;
import com.snaphere.api.post.dto.PostSummaryResponse;
import com.snaphere.api.post.entity.PostEntity;
import com.snaphere.api.post.repository.PostRepository;
import com.snaphere.api.post.tier.PhotoSource;
import com.snaphere.api.post.tier.TrustTier;
import com.snaphere.api.reaction.BookmarkTargetType;
import com.snaphere.api.reaction.entity.BookmarkEntity;
import com.snaphere.api.reaction.repository.BookmarkRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class UserBookmarkPostIdTest {
    @Test
    void bookmarksJoinTheExternalPostIdBackToItsNumericDatabaseKey() {
        UUID userId = UUID.randomUUID();
        UserRepository users = mock(UserRepository.class);
        User user = mock(User.class);
        when(user.getStatus()).thenReturn(UserStatus.ACTIVE);
        when(users.findById(userId)).thenReturn(Optional.of(user));
        PostRepository posts = mock(PostRepository.class);
        PostResponseAssembler assembler = mock(PostResponseAssembler.class);
        BookmarkRepository bookmarks = mock(BookmarkRepository.class);
        PostEntity post = PostEntity.create(userId, 1L, null, 1, "본문", TrustTier.LOW,
                null, null, null, PhotoSource.ALBUM);
        ReflectionTestUtils.setField(post, "postId", 42L);
        PostSummaryResponse summary = PostSummaryResponse.of(post, null, null, List.of(), null, null);
        when(bookmarks.findPage(eq(userId), eq(BookmarkTargetType.POST), isNull(), isNull(), any()))
                .thenReturn(List.of(BookmarkEntity.of(userId, BookmarkTargetType.POST, 42L)));
        when(posts.findAllById(List.of(42L))).thenReturn(List.of(post));
        when(assembler.summaries(List.of(post), Optional.of(userId))).thenReturn(List.of(summary));
        UserService service = new UserService(users, null, null, posts, assembler,
                new PagingProperties(20, 50), null, null, null, null, bookmarks, null, null, null);

        var result = service.bookmarks(userId, BookmarkTargetType.POST, null, 20);

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().getFirst().post().postId()).isEqualTo("pst_16");
    }
}
