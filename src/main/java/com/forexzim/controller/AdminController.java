package com.forexzim.controller;

import com.forexzim.model.BlogPost;
import com.forexzim.repository.AlertSubscriptionRepository;
import com.forexzim.repository.BlogRepository;
import com.forexzim.repository.NewsletterSubscriberRepository;
import com.forexzim.repository.TelegramAlertRepository;
import com.forexzim.service.ScheduledScraperService;
import org.springframework.beans.propertyeditors.StringTrimmerEditor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.beans.PropertyEditorSupport;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.UUID;

@Controller
@RequestMapping("/admin")
public class AdminController {

    private final BlogRepository blogRepository;
    private final NewsletterSubscriberRepository newsletterSubscriberRepository;
    private final AlertSubscriptionRepository alertSubscriptionRepository;
    private final TelegramAlertRepository telegramAlertRepository;
    private final ScheduledScraperService scheduledScraperService;

    public AdminController(BlogRepository blogRepository,
                           NewsletterSubscriberRepository newsletterSubscriberRepository,
                           AlertSubscriptionRepository alertSubscriptionRepository,
                           TelegramAlertRepository telegramAlertRepository,
                           ScheduledScraperService scheduledScraperService) {
        this.blogRepository = blogRepository;
        this.newsletterSubscriberRepository = newsletterSubscriberRepository;
        this.alertSubscriptionRepository = alertSubscriptionRepository;
        this.telegramAlertRepository = telegramAlertRepository;
        this.scheduledScraperService = scheduledScraperService;
    }

    @InitBinder
    public void initBinder(WebDataBinder binder) {
        binder.registerCustomEditor(String.class, new StringTrimmerEditor(false));
        binder.registerCustomEditor(LocalDateTime.class, new PropertyEditorSupport() {
            @Override
            public void setAsText(String text) {
                if (text != null && !text.isBlank()) {
                    try {
                        setValue(LocalDateTime.parse(text, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")));
                    } catch (DateTimeParseException e) {
                        setValue(null);
                    }
                } else {
                    setValue(null);
                }
            }
            @Override
            public String getAsText() {
                LocalDateTime val = (LocalDateTime) getValue();
                return val == null ? "" : val.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"));
            }
        });
    }

    // ── Dashboard ──────────────────────────────────────────────────────────────

    @GetMapping({"", "/"})
    public String dashboard(Model model) {
        var top5 = PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "createdAt"));
        model.addAttribute("recentPosts", blogRepository.findAll(top5).getContent());
        model.addAttribute("newsletterActive", newsletterSubscriberRepository.findByActiveTrue().size());
        model.addAttribute("newsletterTotal", newsletterSubscriberRepository.count());
        model.addAttribute("alertActive", alertSubscriptionRepository.findByActiveTrue().size());
        model.addAttribute("alertTotal", alertSubscriptionRepository.count());
        model.addAttribute("telegramTotal", telegramAlertRepository.count());
        model.addAttribute("totalPosts", blogRepository.count());
        model.addAttribute("publishedPosts",
            blogRepository.findByStatusOrderByPublishedAtDesc(BlogPost.Status.PUBLISHED).size());
        model.addAttribute("activePage", "dashboard");
        return "admin/dashboard";
    }

    @PostMapping("/scrape")
    public String triggerScrape(RedirectAttributes ra) {
        try {
            scheduledScraperService.scrapeAllActiveSources();
            ra.addFlashAttribute("success", "Scrape triggered — rates refreshed.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Scrape failed: " + e.getMessage());
        }
        return "redirect:/admin";
    }

    // ── Login ──────────────────────────────────────────────────────────────────

    @GetMapping("/login")
    public String loginPage() {
        return "admin/login";
    }

    // ── Blog management ────────────────────────────────────────────────────────

    @GetMapping("/blog")
    public String blogList(Model model) {
        model.addAttribute("posts",
            blogRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt")));
        model.addAttribute("activePage", "blog");
        return "admin/blog-list";
    }

    @GetMapping("/blog/new")
    public String newBlogForm(Model model) {
        BlogPost post = new BlogPost();
        post.setAuthor("ZimRate Team");
        post.setReadTimeMinutes(5);
        post.setStatus(BlogPost.Status.DRAFT);
        model.addAttribute("post", post);
        model.addAttribute("statuses", BlogPost.Status.values());
        model.addAttribute("isNew", true);
        model.addAttribute("activePage", "blog");
        return "admin/blog-form";
    }

    @PostMapping("/blog")
    public String createBlog(@ModelAttribute BlogPost post, Model model, RedirectAttributes ra) {
        try {
            LocalDateTime now = LocalDateTime.now();
            post.setCreatedAt(now);
            post.setUpdatedAt(now);
            post.setPreviewToken(UUID.randomUUID().toString());
            if (BlogPost.Status.PUBLISHED.equals(post.getStatus()) && post.getPublishedAt() == null) {
                post.setPublishedAt(now);
            }
            blogRepository.save(post);
            ra.addFlashAttribute("success", "Post \"" + post.getTitle() + "\" created.");
            return "redirect:/admin/blog";
        } catch (Exception e) {
            model.addAttribute("error", friendlyError(e));
            model.addAttribute("statuses", BlogPost.Status.values());
            model.addAttribute("isNew", true);
            model.addAttribute("activePage", "blog");
            return "admin/blog-form";
        }
    }

    @GetMapping("/blog/{id}/edit")
    public String editBlogForm(@PathVariable Long id, Model model) {
        BlogPost post = blogRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Post not found: " + id));
        model.addAttribute("post", post);
        model.addAttribute("statuses", BlogPost.Status.values());
        model.addAttribute("isNew", false);
        model.addAttribute("activePage", "blog");
        return "admin/blog-form";
    }

    @PostMapping("/blog/{id}")
    public String updateBlog(@PathVariable Long id, @ModelAttribute BlogPost form,
                             Model model, RedirectAttributes ra) {
        BlogPost post = blogRepository.findById(id).orElseThrow();
        try {
            post.setTitle(form.getTitle());
            post.setSlug(form.getSlug());
            post.setExcerpt(form.getExcerpt());
            post.setContent(form.getContent());
            post.setAuthor(form.getAuthor());
            post.setMetaDescription(form.getMetaDescription());
            post.setReadTimeMinutes(form.getReadTimeMinutes());
            post.setImageUrl(form.getImageUrl());
            post.setSocialImageUrl(form.getSocialImageUrl());
            post.setPublishAt(form.getPublishAt());
            post.setStatus(form.getStatus());
            post.setUpdatedAt(LocalDateTime.now());
            if (post.getPreviewToken() == null) post.setPreviewToken(UUID.randomUUID().toString());
            if (BlogPost.Status.PUBLISHED.equals(form.getStatus()) && post.getPublishedAt() == null) {
                post.setPublishedAt(LocalDateTime.now());
            }
            blogRepository.save(post);
            ra.addFlashAttribute("success", "Post updated.");
            return "redirect:/admin/blog";
        } catch (Exception e) {
            // post already has the user's input applied — re-render with all system fields intact
            model.addAttribute("post", post);
            model.addAttribute("error", friendlyError(e));
            model.addAttribute("statuses", BlogPost.Status.values());
            model.addAttribute("isNew", false);
            model.addAttribute("activePage", "blog");
            return "admin/blog-form";
        }
    }

    @PostMapping("/blog/{id}/publish")
    public String publishPost(@PathVariable Long id, RedirectAttributes ra) {
        BlogPost post = blogRepository.findById(id).orElseThrow();
        post.setStatus(BlogPost.Status.PUBLISHED);
        if (post.getPublishedAt() == null) post.setPublishedAt(LocalDateTime.now());
        post.setUpdatedAt(LocalDateTime.now());
        blogRepository.save(post);
        ra.addFlashAttribute("success", "Post published.");
        return "redirect:/admin/blog";
    }

    @PostMapping("/blog/{id}/unpublish")
    public String unpublishPost(@PathVariable Long id, RedirectAttributes ra) {
        BlogPost post = blogRepository.findById(id).orElseThrow();
        post.setStatus(BlogPost.Status.DRAFT);
        post.setUpdatedAt(LocalDateTime.now());
        blogRepository.save(post);
        ra.addFlashAttribute("success", "Post moved back to Draft.");
        return "redirect:/admin/blog";
    }

    @PostMapping("/blog/{id}/delete")
    public String deletePost(@PathVariable Long id, RedirectAttributes ra) {
        blogRepository.deleteById(id);
        ra.addFlashAttribute("success", "Post deleted.");
        return "redirect:/admin/blog";
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private String friendlyError(Exception e) {
        Throwable root = e;
        while (root.getCause() != null) root = root.getCause();
        String msg = root.getMessage() != null ? root.getMessage() : e.getMessage();
        if (msg != null && msg.contains("slug")) return "That slug is already in use — choose a different one.";
        if (msg != null && (msg.contains("duplicate key") || msg.contains("unique constraint")))
            return "A duplicate value was detected. Check the slug and other unique fields.";
        return msg != null ? msg : "An unexpected error occurred.";
    }

    // ── Subscribers ────────────────────────────────────────────────────────────

    @GetMapping("/subscribers")
    public String subscribers(Model model) {
        model.addAttribute("newsletterActive", newsletterSubscriberRepository.findByActiveTrue().size());
        model.addAttribute("newsletterTotal", newsletterSubscriberRepository.count());
        model.addAttribute("alertActive", alertSubscriptionRepository.findByActiveTrue().size());
        model.addAttribute("alertTotal", alertSubscriptionRepository.count());
        model.addAttribute("telegramTotal", telegramAlertRepository.count());
        model.addAttribute("activePage", "subscribers");
        return "admin/subscribers";
    }
}
