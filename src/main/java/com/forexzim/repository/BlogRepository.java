package com.forexzim.repository;

import com.forexzim.model.BlogPost;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BlogRepository extends JpaRepository<BlogPost, Long> {

    List<BlogPost> findByStatusOrderByPublishedAtDesc(BlogPost.Status status);

    Optional<BlogPost> findBySlugAndStatus(String slug, BlogPost.Status status);

    List<BlogPost> findByStatusAndTelegramNotifiedFalse(BlogPost.Status status);
}
