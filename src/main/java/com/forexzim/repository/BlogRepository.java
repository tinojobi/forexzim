package com.forexzim.repository;

import com.forexzim.model.BlogPost;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface BlogRepository extends JpaRepository<BlogPost, Long> {

    List<BlogPost> findByStatusOrderByPublishedAtDesc(BlogPost.Status status);

    @Query("SELECT p FROM BlogPost p WHERE p.status = :status AND p.publishAt IS NOT NULL AND p.publishAt <= :now")
    List<BlogPost> findScheduledPostsReady(@Param("status") BlogPost.Status status, @Param("now") LocalDateTime now);

    Optional<BlogPost> findBySlugAndStatus(String slug, BlogPost.Status status);

    List<BlogPost> findByStatusAndTelegramNotifiedFalse(BlogPost.Status status);

    List<BlogPost> findByStatusAndNewsletterNotifiedFalse(BlogPost.Status status);

    boolean existsBySlug(String slug);

    boolean existsBySlugAndIdNot(String slug, Long id);

    Optional<BlogPost> findBySlug(String slug);

    List<BlogPost> findTop3ByStatusAndIdNotOrderByPublishedAtDesc(BlogPost.Status status, Long id);

    List<BlogPost> findTop3ByStatusOrderByPublishedAtDesc(BlogPost.Status status);
}
