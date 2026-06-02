package com.forexzim.controller;

import com.forexzim.model.AlertSubscription;
import com.forexzim.model.BlogPost;
import com.forexzim.model.NewsletterSubscriber;
import com.forexzim.model.TelegramAlert;
import com.forexzim.repository.AlertSubscriptionRepository;
import com.forexzim.repository.BlogRepository;
import com.forexzim.repository.NewsletterSubscriberRepository;
import com.forexzim.repository.RateRepository;
import com.forexzim.repository.TelegramAlertRepository;
import com.forexzim.service.NewsletterService;
import com.forexzim.service.ScheduledScraperService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.propertyeditors.StringTrimmerEditor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.beans.PropertyEditorSupport;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Controller
@RequestMapping("/admin")
public class AdminController {

    @Value("${zimrate.uploads.dir:./uploads/}")
    private String uploadsDir;

    private final BlogRepository blogRepository;
    private final NewsletterSubscriberRepository newsletterSubscriberRepository;
    private final AlertSubscriptionRepository alertSubscriptionRepository;
    private final TelegramAlertRepository telegramAlertRepository;
    private final ScheduledScraperService scheduledScraperService;
    private final RateRepository rateRepository;
    private final NewsletterService newsletterService;

    public AdminController(BlogRepository blogRepository,
                           NewsletterSubscriberRepository newsletterSubscriberRepository,
                           AlertSubscriptionRepository alertSubscriptionRepository,
                           TelegramAlertRepository telegramAlertRepository,
                           ScheduledScraperService scheduledScraperService,
                           RateRepository rateRepository,
                           NewsletterService newsletterService) {
        this.blogRepository = blogRepository;
        this.newsletterSubscriberRepository = newsletterSubscriberRepository;
        this.alertSubscriptionRepository = alertSubscriptionRepository;
        this.telegramAlertRepository = telegramAlertRepository;
        this.scheduledScraperService = scheduledScraperService;
        this.rateRepository = rateRepository;
        this.newsletterService = newsletterService;
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

    // ── Slug duplicate check ───────────────────────────────────────────────────

    @GetMapping("/blog/check-slug")
    @ResponseBody
    public Map<String, Boolean> checkSlug(@RequestParam String slug,
                                           @RequestParam(required = false) Long excludeId) {
        boolean taken = excludeId != null
            ? blogRepository.existsBySlugAndIdNot(slug, excludeId)
            : blogRepository.existsBySlug(slug);
        return Map.of("available", !taken);
    }

    // ── Rate data table ────────────────────────────────────────────────────────

    @GetMapping("/rates")
    public String rates(Model model) {
        model.addAttribute("rates", rateRepository.findLatestBySourceAndCurrencyPair());
        model.addAttribute("activePage", "rates");
        return "admin/rates";
    }

    // ── Newsletter compose & send ──────────────────────────────────────────────

    @GetMapping("/newsletter")
    public String newsletterCompose(Model model) {
        model.addAttribute("activePage", "newsletter");
        return "admin/newsletter";
    }

    @PostMapping("/newsletter/send")
    public String newsletterSend(@RequestParam String subject,
                                  @RequestParam String body,
                                  @RequestParam(defaultValue = "false") boolean testOnly,
                                  @RequestParam(required = false) String testEmail,
                                  RedirectAttributes ra) {
        try {
            if (testOnly && testEmail != null && !testEmail.isBlank()) {
                newsletterService.sendTestEmail(subject, body, testEmail);
                ra.addFlashAttribute("success", "Test email sent to " + testEmail);
            } else {
                int count = newsletterService.sendManualNewsletter(subject, body);
                ra.addFlashAttribute("success", "Newsletter sent to " + count + " subscriber(s).");
            }
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Send failed: " + e.getMessage());
        }
        return "redirect:/admin/newsletter";
    }

    @PostMapping("/newsletter/preview")
    @ResponseBody
    public String newsletterPreview(@RequestParam String subject, @RequestParam String body) {
        return newsletterService.previewHtml(subject, body);
    }

    // ── Image upload ───────────────────────────────────────────────────────────

    @PostMapping("/upload")
    @ResponseBody
    public ResponseEntity<Map<String, String>> uploadImage(@RequestParam("file") MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            return ResponseEntity.badRequest().body(Map.of("error", "Only image files are allowed."));
        }
        try {
            String ext = StringUtils.getFilenameExtension(
                StringUtils.cleanPath(file.getOriginalFilename() != null ? file.getOriginalFilename() : "img"));
            String filename = UUID.randomUUID() + (ext != null && !ext.isBlank() ? "." + ext : "");
            Path dir = Paths.get(uploadsDir);
            Files.createDirectories(dir);
            Files.copy(file.getInputStream(), dir.resolve(filename), StandardCopyOption.REPLACE_EXISTING);
            return ResponseEntity.ok(Map.of("url", "/uploads/" + filename));
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Upload failed: " + e.getMessage()));
        }
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
        List<NewsletterSubscriber> newsletterSubs = newsletterSubscriberRepository
            .findAll(Sort.by(Sort.Direction.DESC, "subscribedAt"));
        List<AlertSubscription> emailAlerts = alertSubscriptionRepository
            .findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
        List<TelegramAlert> telegramAlerts = telegramAlertRepository
            .findAll(Sort.by(Sort.Direction.DESC, "createdAt"));

        model.addAttribute("newsletterActive", newsletterSubs.stream().filter(s -> Boolean.TRUE.equals(s.getActive())).count());
        model.addAttribute("newsletterTotal", (long) newsletterSubs.size());
        model.addAttribute("alertActive", emailAlerts.stream().filter(a -> Boolean.TRUE.equals(a.getActive())).count());
        model.addAttribute("alertTotal", (long) emailAlerts.size());
        model.addAttribute("telegramTotal", (long) telegramAlerts.size());

        model.addAttribute("newsletterSubs", newsletterSubs);
        model.addAttribute("emailAlerts", emailAlerts);
        model.addAttribute("telegramAlerts", telegramAlerts);
        model.addAttribute("activePage", "subscribers");
        return "admin/subscribers";
    }
}
