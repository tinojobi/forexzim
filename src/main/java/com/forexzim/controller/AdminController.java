package com.forexzim.controller;

import com.forexzim.model.AlertSubscription;
import com.forexzim.model.BlogPost;
import com.forexzim.model.NewsletterSubscriber;
import com.forexzim.model.Rate;
import com.forexzim.model.TelegramAlert;
import com.forexzim.repository.AlertSubscriptionRepository;
import com.forexzim.repository.BlogRepository;
import com.forexzim.repository.NewsletterSubscriberRepository;
import com.forexzim.repository.RateRepository;
import com.forexzim.repository.TelegramAlertRepository;
import com.forexzim.service.GscService;
import com.forexzim.service.NewsletterService;
import com.forexzim.service.ScheduledScraperService;
import com.forexzim.service.SystemEventService;
import com.forexzim.service.TelegramBotService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.propertyeditors.StringTrimmerEditor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
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
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin")
public class AdminController {

    @Value("${zimrate.uploads.dir:./uploads/}")
    private String uploadsDir;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    @Value("${zimrate.telegram.bot-token:}")
    private String telegramBotToken;

    @Value("${zimrate.admin.token:}")
    private String adminToken;

    private final BlogRepository blogRepository;
    private final NewsletterSubscriberRepository newsletterSubscriberRepository;
    private final AlertSubscriptionRepository alertSubscriptionRepository;
    private final TelegramAlertRepository telegramAlertRepository;
    private final ScheduledScraperService scheduledScraperService;
    private final RateRepository rateRepository;
    private final GscService gscService;
    private final NewsletterService newsletterService;
    private final SystemEventService systemEventService;
    private final TelegramBotService telegramBotService;

    public AdminController(BlogRepository blogRepository,
                           NewsletterSubscriberRepository newsletterSubscriberRepository,
                           AlertSubscriptionRepository alertSubscriptionRepository,
                           TelegramAlertRepository telegramAlertRepository,
                           ScheduledScraperService scheduledScraperService,
                           RateRepository rateRepository,
                           GscService gscService,
                           NewsletterService newsletterService,
                           SystemEventService systemEventService,
                           TelegramBotService telegramBotService) {
        this.blogRepository = blogRepository;
        this.newsletterSubscriberRepository = newsletterSubscriberRepository;
        this.alertSubscriptionRepository = alertSubscriptionRepository;
        this.telegramAlertRepository = telegramAlertRepository;
        this.scheduledScraperService = scheduledScraperService;
        this.rateRepository = rateRepository;
        this.gscService = gscService;
        this.newsletterService = newsletterService;
        this.systemEventService = systemEventService;
        this.telegramBotService = telegramBotService;
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
        // ── Greeting ──────────────────────────────────────────────────────────
        int hour = LocalTime.now(ZoneId.of("Africa/Harare")).getHour();
        model.addAttribute("greeting", hour < 12 ? "Good morning" : hour < 17 ? "Good afternoon" : "Good evening");

        // ── Recent posts + quick-publish drafts ───────────────────────────────
        var top5 = PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "createdAt"));
        model.addAttribute("recentPosts", blogRepository.findAll(top5).getContent());

        List<BlogPost> allPosts = blogRepository.findAll();
        long publishedCount  = allPosts.stream().filter(p -> p.getStatus() == BlogPost.Status.PUBLISHED).count();
        long draftCount      = allPosts.stream().filter(p -> p.getStatus() == BlogPost.Status.DRAFT).count();
        long rejectedCount   = allPosts.stream().filter(p -> p.getStatus() == BlogPost.Status.REJECTED).count();
        model.addAttribute("totalPosts",    allPosts.size());
        model.addAttribute("publishedPosts", publishedCount);
        model.addAttribute("draftPosts",    draftCount);
        model.addAttribute("rejectedPosts", rejectedCount);

        List<BlogPost> draftPostsList = allPosts.stream()
            .filter(p -> p.getStatus() == BlogPost.Status.DRAFT)
            .sorted(Comparator.comparing(BlogPost::getCreatedAt).reversed())
            .limit(5)
            .collect(Collectors.toList());
        model.addAttribute("draftPostsList", draftPostsList);

        // ── Subscriber counts ─────────────────────────────────────────────────
        List<NewsletterSubscriber> allSubs = newsletterSubscriberRepository.findAll();
        long newsletterActive = allSubs.stream().filter(s -> Boolean.TRUE.equals(s.getActive())).count();
        model.addAttribute("newsletterActive", newsletterActive);
        model.addAttribute("newsletterTotal",  allSubs.size());
        model.addAttribute("alertActive",  alertSubscriptionRepository.findByActiveTrue().size());
        model.addAttribute("alertTotal",   alertSubscriptionRepository.count());
        model.addAttribute("telegramTotal", telegramAlertRepository.count());

        // ── Subscriber growth chart (last 6 months) ───────────────────────────
        LocalDate today = LocalDate.now(ZoneId.of("Africa/Harare"));
        List<String> chartLabels = new ArrayList<>();
        List<Long>   chartValues = new ArrayList<>();
        for (int i = 5; i >= 0; i--) {
            LocalDate start = today.minusMonths(i).withDayOfMonth(1);
            LocalDate end   = start.plusMonths(1);
            chartLabels.add(start.format(DateTimeFormatter.ofPattern("MMM yy")));
            chartValues.add(allSubs.stream()
                .filter(s -> s.getSubscribedAt() != null)
                .filter(s -> { LocalDate d = s.getSubscribedAt().toLocalDate(); return !d.isBefore(start) && d.isBefore(end); })
                .count());
        }
        model.addAttribute("chartLabels", chartLabels);
        model.addAttribute("chartValues", chartValues);

        // ── Live rate + scrape health ─────────────────────────────────────────
        List<Rate> latestRates   = rateRepository.findLatestBySourceAndCurrencyPair();
        List<Rate> previousRates = rateRepository.findPreviousBySourceAndCurrencyPair();

        Rate liveRate = latestRates.stream()
            .filter(r -> r.getCurrencyPair() != null &&
                        (r.getCurrencyPair().toUpperCase().contains("ZWG") ||
                         r.getCurrencyPair().toUpperCase().contains("ZIG")))
            .findFirst().orElse(null);
        model.addAttribute("liveRate", liveRate);

        if (liveRate != null) {
            previousRates.stream()
                .filter(r -> r.getSource().getId().equals(liveRate.getSource().getId())
                          && r.getCurrencyPair().equals(liveRate.getCurrencyPair()))
                .findFirst()
                .ifPresent(prev -> {
                    if (liveRate.getBuyRate() != null && prev.getBuyRate() != null && prev.getBuyRate().compareTo(BigDecimal.ZERO) != 0) {
                        BigDecimal delta = liveRate.getBuyRate().subtract(prev.getBuyRate());
                        BigDecimal pct   = delta.divide(prev.getBuyRate(), 6, RoundingMode.HALF_UP)
                                               .multiply(BigDecimal.valueOf(100))
                                               .setScale(2, RoundingMode.HALF_UP);
                        model.addAttribute("rateDelta",    delta.setScale(4, RoundingMode.HALF_UP));
                        model.addAttribute("rateDeltaPct", pct);
                    }
                });
        }

        LocalDateTime lastScrape = latestRates.stream()
            .map(Rate::getScrapedAt).filter(t -> t != null)
            .max(Comparator.naturalOrder()).orElse(null);
        model.addAttribute("lastScrape", lastScrape);
        if (lastScrape != null) {
            long mins = Duration.between(lastScrape, LocalDateTime.now()).toMinutes();
            model.addAttribute("scrapeMinutesAgo", mins);
            model.addAttribute("scrapeStatus", mins < 35 ? "healthy" : mins < 70 ? "warning" : "danger");
        }

        // ── System health ─────────────────────────────────────────────────────
        boolean uploadsOk = false;
        try { Path up = Paths.get(uploadsDir); Files.createDirectories(up); uploadsOk = Files.isWritable(up); } catch (Exception ignored) {}
        model.addAttribute("emailHealthy",    mailUsername != null && !mailUsername.isBlank());
        model.addAttribute("telegramHealthy", telegramBotToken != null && !telegramBotToken.isBlank());
        model.addAttribute("uploadsHealthy",  uploadsOk);

        // ── Last published post freshness ─────────────────────────────────────
        Optional<BlogPost> lastPublished = allPosts.stream()
            .filter(p -> p.getStatus() == BlogPost.Status.PUBLISHED && p.getPublishedAt() != null)
            .max(Comparator.comparing(BlogPost::getPublishedAt));
        if (lastPublished.isPresent()) {
            long days = Duration.between(lastPublished.get().getPublishedAt(), LocalDateTime.now()).toDays();
            model.addAttribute("lastPublishedDaysAgo", (int) days);
        }

        // ── Posts published this week ────────────────────────────────────────
        long postsThisWeek = allPosts.stream()
            .filter(p -> p.getStatus() == BlogPost.Status.PUBLISHED && p.getPublishedAt() != null)
            .filter(p -> p.getPublishedAt().isAfter(LocalDateTime.now().minusDays(7)))
            .count();
        model.addAttribute("postsThisWeek", (int) postsThisWeek);

        // ── 7-day rate trend (CBZ USD/ZWG) ──────────────────────────────────
        try {
            List<Object[]> trend = rateRepository.findDailyAverages("CBZ", "USD/ZWG", 7);
            model.addAttribute("rateTrend7d", trend);
        } catch (Exception e) {
            model.addAttribute("rateTrend7d", java.util.Collections.emptyList());
        }

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

    @GetMapping("/calendar")
    public String calendar() {
        return "admin/calendar";
    }

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
        model.addAttribute("telegramBotConfigured", telegramBotService.isConfigured());
        model.addAttribute("telegramBlastCount", telegramBotService.getBlastRecipientCount());
        model.addAttribute("activePage", "subscribers");
        return "admin/subscribers";
    }

    // ── Telegram blast ────────────────────────────────────────────────────────

    @PostMapping("/telegram/blast")
    @ResponseBody
    public ResponseEntity<?> telegramBlast(@RequestBody Map<String, String> body) {
        String message = body.get("message");
        if (message == null || message.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "message is required"));
        }
        if (!telegramBotService.isConfigured()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Telegram bot is not configured"));
        }
        int count = telegramBotService.sendBlast(message);
        return ResponseEntity.ok(Map.of("sent", count));
    }

    // ── GSC Insights page ─────────────────────────────────────────────────────

    @GetMapping("/gsc")
    public String gscPage(Model model) {
        model.addAttribute("adminToken", adminToken);
        model.addAttribute("activePage", "gsc");
        return "admin/gsc";
    }

    // ── Error Log page ────────────────────────────────────────────────────────

    @GetMapping("/error-log")
    public String errorLog(Model model) {
        model.addAttribute("adminToken", adminToken);
        model.addAttribute("activePage", "error-log");
        return "admin/error-log";
    }

    // ── Audit Log page ────────────────────────────────────────────────────────

    @GetMapping("/audit-log")
    public String auditLog(Model model) {
        model.addAttribute("adminToken", adminToken);
        model.addAttribute("activePage", "audit-log");
        return "admin/audit-log";
    }

    // ── Tag/Category Manager ──────────────────────────────────────────────────

    @GetMapping("/categories")
    public String categories(Model model) {
        List<BlogPost> allPosts = blogRepository.findAll();

        List<Map.Entry<String, Long>> categories = allPosts.stream()
            .filter(p -> p.getCategory() != null && !p.getCategory().isBlank())
            .collect(Collectors.groupingBy(BlogPost::getCategory, Collectors.counting()))
            .entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .collect(Collectors.toList());

        List<Map.Entry<String, Long>> keywords = allPosts.stream()
            .filter(p -> p.getKeywords() != null && !p.getKeywords().isBlank())
            .flatMap(p -> java.util.Arrays.stream(p.getKeywords().split(",")))
            .map(String::trim)
            .filter(k -> !k.isBlank())
            .collect(Collectors.groupingBy(k -> k, Collectors.counting()))
            .entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .collect(Collectors.toList());

        model.addAttribute("categories", categories);
        model.addAttribute("keywords", keywords);
        model.addAttribute("adminToken", adminToken);
        model.addAttribute("activePage", "categories");
        return "admin/categories";
    }

    // ── Article Performance (GSC data) ─────────────────────────────────────────

    @GetMapping("/article-performance")
    @ResponseBody
    public ResponseEntity<?> articlePerformance(
            @RequestHeader(value = "X-Admin-Token", required = false) String token) {

        if (adminToken.isBlank() || !adminToken.equals(token)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Invalid or missing X-Admin-Token header."));
        }

        return ResponseEntity.ok(gscService.getArticlePerformance());
    }

    // ── Event Log ───────────────────────────────────────────────────────────────

    @GetMapping("/events")
    @ResponseBody
    public ResponseEntity<?> getEvents(
            @RequestHeader(value = "X-Admin-Token", required = false) String token,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String from,
            @RequestParam(defaultValue = "50") int limit) {

        if (adminToken.isBlank() || !adminToken.equals(token)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Invalid or missing X-Admin-Token header."));
        }
        return ResponseEntity.ok(Map.of(
            "events", systemEventService.getEvents(type, from, limit)
        ));
    }
}
