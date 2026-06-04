package com.forexzim.controller;

import com.forexzim.model.BlogPost;
import com.forexzim.repository.BlogRepository;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Automatically injects sidebar badge counts into every admin Thymeleaf model.
 * Scoped to AdminController so it doesn't run on public pages.
 */
@ControllerAdvice(assignableTypes = AdminController.class)
public class AdminModelAdvice {

    private final BlogRepository blogRepository;

    public AdminModelAdvice(BlogRepository blogRepository) {
        this.blogRepository = blogRepository;
    }

    @ModelAttribute("sidebarDraftCount")
    public long sidebarDraftCount() {
        return blogRepository.findAll().stream()
            .filter(p -> p.getStatus() == BlogPost.Status.DRAFT)
            .count();
    }
}
