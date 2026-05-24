package com.forexzim.controller;

import com.forexzim.model.BlogPost;
import com.forexzim.repository.BlogRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(BlogController.class)
class BlogControllerTest {

    private static final String PUBLISHED_SLUG = "published-market-update";
    private static final String DRAFT_SLUG = "draft-market-update";
    private static final String PREVIEW_TOKEN = "123e4567-e89b-12d3-a456-426614174000";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BlogRepository blogRepository;

    @Test
    void publishedPostRendersNormally() throws Exception {
        BlogPost post = blogPost(PUBLISHED_SLUG, BlogPost.Status.PUBLISHED, "Published market update", "Published content", PREVIEW_TOKEN);
        BlogPost related = blogPost("related-update", BlogPost.Status.PUBLISHED, "Related update", "Related content", null);
        when(blogRepository.findBySlugAndStatus(PUBLISHED_SLUG, BlogPost.Status.PUBLISHED)).thenReturn(Optional.of(post));
        when(blogRepository.findTop3ByStatusAndIdNotOrderByPublishedAtDesc(BlogPost.Status.PUBLISHED, post.getId()))
                .thenReturn(List.of(related));

        mockMvc.perform(get("/blog/{slug}", PUBLISHED_SLUG))
                .andExpect(status().isOk())
                .andExpect(view().name("blog-post"))
                .andExpect(model().attribute("post", post))
                .andExpect(model().attribute("isPreview", false))
                .andExpect(content().string(containsString("Published market update")))
                .andExpect(content().string(containsString("Published content")))
                .andExpect(content().string(containsString("application/ld+json")))
                .andExpect(content().string(not(containsString("Draft preview"))))
                .andExpect(content().string(not(containsString("noindex, nofollow"))));
    }

    @Test
    void draftPostWithoutPreviewTokenReturnsNotFound() throws Exception {
        when(blogRepository.findBySlugAndStatus(DRAFT_SLUG, BlogPost.Status.PUBLISHED)).thenReturn(Optional.empty());

        mockMvc.perform(get("/blog/{slug}", DRAFT_SLUG))
                .andExpect(status().isNotFound());

        verify(blogRepository, never()).findBySlug(DRAFT_SLUG);
    }

    @Test
    void draftPostWithWrongPreviewTokenReturnsNotFound() throws Exception {
        BlogPost draft = blogPost(DRAFT_SLUG, BlogPost.Status.DRAFT, "Draft market update", "Draft content", PREVIEW_TOKEN);
        when(blogRepository.findBySlug(DRAFT_SLUG)).thenReturn(Optional.of(draft));

        mockMvc.perform(get("/blog/{slug}", DRAFT_SLUG).param("preview", "wrong-token"))
                .andExpect(status().isNotFound());
    }

    @Test
    void draftPostWithValidPreviewTokenRendersPreviewNoindexAndSuppressesJsonLd() throws Exception {
        BlogPost draft = blogPost(DRAFT_SLUG, BlogPost.Status.DRAFT, "Draft market update", "Draft preview content", PREVIEW_TOKEN);
        when(blogRepository.findBySlug(DRAFT_SLUG)).thenReturn(Optional.of(draft));

        mockMvc.perform(get("/blog/{slug}", DRAFT_SLUG).param("preview", PREVIEW_TOKEN))
                .andExpect(status().isOk())
                .andExpect(view().name("blog-post"))
                .andExpect(model().attribute("post", draft))
                .andExpect(model().attribute("isPreview", true))
                .andExpect(content().string(containsString("Draft preview")))
                .andExpect(content().string(containsString("Draft preview content")))
                .andExpect(content().string(containsString("name=\"robots\" content=\"noindex, nofollow\"")))
                .andExpect(content().string(not(containsString("application/ld+json"))));

        verify(blogRepository, never()).findTop3ByStatusAndIdNotOrderByPublishedAtDesc(any(), any());
    }

    @Test
    void publishedPostWithPreviewParameterStillRendersNormally() throws Exception {
        BlogPost post = blogPost(PUBLISHED_SLUG, BlogPost.Status.PUBLISHED, "Published market update", "Published content", PREVIEW_TOKEN);
        when(blogRepository.findBySlug(PUBLISHED_SLUG)).thenReturn(Optional.of(post));
        when(blogRepository.findTop3ByStatusAndIdNotOrderByPublishedAtDesc(BlogPost.Status.PUBLISHED, post.getId()))
                .thenReturn(List.of());

        mockMvc.perform(get("/blog/{slug}", PUBLISHED_SLUG).param("preview", "anything"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("isPreview", false))
                .andExpect(content().string(containsString("Published market update")))
                .andExpect(content().string(containsString("application/ld+json")))
                .andExpect(content().string(not(containsString("Draft preview"))))
                .andExpect(content().string(not(containsString("noindex, nofollow"))));
    }

    private BlogPost blogPost(String slug, BlogPost.Status status, String title, String content, String previewToken) {
        LocalDateTime now = LocalDateTime.of(2026, 5, 22, 10, 30);
        BlogPost post = new BlogPost();
        post.setId(Math.abs((long) slug.hashCode()));
        post.setSlug(slug);
        post.setStatus(status);
        post.setTitle(title);
        post.setExcerpt(title + " excerpt");
        post.setContent("<p>" + content + "</p>");
        post.setAuthor("ZimRate Team");
        post.setMetaDescription(title + " meta description");
        post.setReadTimeMinutes(4);
        post.setCreatedAt(now.minusDays(2));
        post.setUpdatedAt(now.minusDays(1));
        post.setPublishedAt(now);
        post.setPreviewToken(previewToken);
        return post;
    }
}
