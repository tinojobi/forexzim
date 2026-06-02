package com.forexzim.service;

import com.forexzim.model.BlogPost;
import com.forexzim.model.NewsletterSubscriber;
import com.forexzim.repository.BlogRepository;
import com.forexzim.repository.NewsletterSubscriberRepository;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class NewsletterService {

    private static final Logger log = LoggerFactory.getLogger(NewsletterService.class);

    private final NewsletterSubscriberRepository subscriberRepository;
    private final BlogRepository blogRepository;
    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    @Value("${forexzim.alerts.from:noreply@zimrate.com}")
    private String fromEmail;

    @Value("${zimrate.base-url:https://zimrate.com}")
    private String baseUrl;

    public NewsletterService(NewsletterSubscriberRepository subscriberRepository,
                             BlogRepository blogRepository,
                             JavaMailSender mailSender) {
        this.subscriberRepository = subscriberRepository;
        this.blogRepository = blogRepository;
        this.mailSender = mailSender;
    }

    public enum SubscribeResult { SUBSCRIBED, ALREADY_ACTIVE, REACTIVATED }

    public SubscribeResult subscribe(String email) {
        email = email.trim().toLowerCase();
        var existing = subscriberRepository.findByEmail(email);
        if (existing.isPresent()) {
            NewsletterSubscriber sub = existing.get();
            if (sub.getActive()) {
                return SubscribeResult.ALREADY_ACTIVE;
            }
            sub.setActive(true);
            subscriberRepository.save(sub);
            sendWelcomeEmail(sub);
            return SubscribeResult.REACTIVATED;
        }
        NewsletterSubscriber sub = new NewsletterSubscriber();
        sub.setEmail(email);
        sub.setToken(UUID.randomUUID().toString());
        subscriberRepository.save(sub);
        sendWelcomeEmail(sub);
        return SubscribeResult.SUBSCRIBED;
    }

    public boolean unsubscribeByToken(String token) {
        return subscriberRepository.findByToken(token).map(sub -> {
            sub.setActive(false);
            subscriberRepository.save(sub);
            return true;
        }).orElse(false);
    }

    @Scheduled(fixedDelay = 5 * 60 * 1000, initialDelay = 3 * 60 * 1000)
    public void sendPendingNotifications() {
        if (mailUsername == null || mailUsername.isBlank()) {
            log.debug("MAIL_USERNAME not set — newsletter notifications skipped");
            return;
        }
        List<BlogPost> posts = blogRepository.findByStatusAndNewsletterNotifiedFalse(BlogPost.Status.PUBLISHED);
        if (posts.isEmpty()) return;

        List<NewsletterSubscriber> subscribers = subscriberRepository.findByActiveTrue();

        for (BlogPost post : posts) {
            int sent = 0;
            for (NewsletterSubscriber sub : subscribers) {
                try {
                    sendPostNotificationEmail(post, sub);
                    sent++;
                } catch (Exception e) {
                    log.error("Failed to send newsletter to {}: {}", sub.getEmail(), e.getMessage());
                }
            }
            post.setNewsletterNotified(true);
            blogRepository.save(post);
            log.info("Newsletter sent for post '{}' to {} subscriber(s)", post.getTitle(), sent);
        }
    }

    private void sendWelcomeEmail(NewsletterSubscriber sub) {
        try {
            String unsubscribeUrl = baseUrl + "/unsubscribe/newsletter/" + sub.getToken();
            String html = "<!DOCTYPE html>" +
                "<html lang='en'><head><meta charset='UTF-8'>" +
                "<meta name='viewport' content='width=device-width,initial-scale=1'></head>" +
                "<body style='margin:0;padding:0;background:#f1f5f9;font-family:Inter,Arial,sans-serif;'>" +
                "<table width='100%' cellpadding='0' cellspacing='0' style='background:#f1f5f9;padding:40px 16px;'><tr><td align='center'>" +
                "<table width='560' cellpadding='0' cellspacing='0' style='background:#ffffff;border-radius:16px;overflow:hidden;box-shadow:0 4px 24px rgba(0,0,0,0.08);'>" +
                "<tr><td style='background:linear-gradient(135deg,#14532d 0%,#166534 100%);padding:36px 40px;text-align:center;'>" +
                "<div style='font-size:32px;font-weight:800;letter-spacing:-1px;'>" +
                "<span style='color:#ffffff;'>Zim</span><span style='color:#ca8a04;'>Rate</span></div>" +
                "<p style='margin:8px 0 0;color:rgba(255,255,255,0.7);font-size:13px;letter-spacing:0.3px;'>Zimbabwe Forex Rates</p>" +
                "</td></tr>" +
                "<tr><td style='padding:36px 40px;'>" +
                "<h2 style='margin:0 0 12px;font-size:22px;font-weight:700;color:#0f172a;'>You're subscribed</h2>" +
                "<p style='margin:0 0 28px;color:#64748b;font-size:14px;line-height:1.6;'>You'll get an email whenever we publish a new article about Zimbabwe's forex market, exchange rates, and economic news.</p>" +
                "<table width='100%' cellpadding='0' cellspacing='0'><tr><td align='center' style='padding-bottom:8px;'>" +
                "<a href='" + baseUrl + "/blog' style='display:inline-block;background:#14532d;color:#ffffff;font-weight:700;font-size:15px;" +
                "text-decoration:none;padding:15px 36px;border-radius:10px;letter-spacing:0.2px;'>Browse the Blog →</a>" +
                "</td></tr></table>" +
                "</td></tr>" +
                "<tr><td style='background:#f8fafc;padding:24px 40px;text-align:center;border-top:1px solid #e2e8f0;'>" +
                "<a href='" + unsubscribeUrl + "' style='font-size:12px;color:#94a3b8;text-decoration:underline;'>Unsubscribe</a>" +
                "</td></tr>" +
                "</table></td></tr></table>" +
                "</body></html>";

            MimeMessage mime = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, "UTF-8");
            helper.setFrom(fromEmail, "ZimRate");
            helper.setTo(sub.getEmail());
            helper.setSubject("You're subscribed to ZimRate Blog");
            helper.setText(html, true);
            mailSender.send(mime);
        } catch (Exception e) {
            log.error("Failed to send welcome email to {}: {}", sub.getEmail(), e.getMessage());
        }
    }

    private void sendPostNotificationEmail(BlogPost post, NewsletterSubscriber sub) throws Exception {
        String unsubscribeUrl = baseUrl + "/unsubscribe/newsletter/" + sub.getToken();
        String articleUrl = baseUrl + "/blog/" + post.getSlug();
        String excerpt = post.getExcerpt() != null ? post.getExcerpt() : post.getMetaDescription();
        String readTime = post.getReadTimeMinutes() != null ? post.getReadTimeMinutes() + " min read" : "";

        String html = "<!DOCTYPE html>" +
            "<html lang='en'><head><meta charset='UTF-8'>" +
            "<meta name='viewport' content='width=device-width,initial-scale=1'></head>" +
            "<body style='margin:0;padding:0;background:#f1f5f9;font-family:Inter,Arial,sans-serif;'>" +
            "<table width='100%' cellpadding='0' cellspacing='0' style='background:#f1f5f9;padding:40px 16px;'><tr><td align='center'>" +
            "<table width='560' cellpadding='0' cellspacing='0' style='background:#ffffff;border-radius:16px;overflow:hidden;box-shadow:0 4px 24px rgba(0,0,0,0.08);'>" +
            "<tr><td style='background:linear-gradient(135deg,#14532d 0%,#166534 100%);padding:36px 40px;text-align:center;'>" +
            "<div style='font-size:32px;font-weight:800;letter-spacing:-1px;'>" +
            "<span style='color:#ffffff;'>Zim</span><span style='color:#ca8a04;'>Rate</span></div>" +
            "<p style='margin:8px 0 0;color:rgba(255,255,255,0.7);font-size:13px;letter-spacing:0.3px;'>Zimbabwe Forex Rates</p>" +
            "</td></tr>" +
            "<tr><td style='padding:36px 40px;'>" +
            "<p style='margin:0 0 8px;font-size:11px;font-weight:700;color:#16a34a;text-transform:uppercase;letter-spacing:0.08em;'>New Article</p>" +
            "<h2 style='margin:0 0 16px;font-size:22px;font-weight:700;color:#0f172a;line-height:1.3;'>" + escapeHtml(post.getTitle()) + "</h2>" +
            (excerpt != null ? "<p style='margin:0 0 16px;color:#64748b;font-size:14px;line-height:1.6;'>" + escapeHtml(excerpt) + "</p>" : "") +
            (!readTime.isEmpty() ? "<p style='margin:0 0 24px;'><span style='display:inline-block;background:#f0fdf4;color:#14532d;font-size:12px;font-weight:600;padding:4px 10px;border-radius:20px;border:1px solid #bbf7d0;'>" + readTime + "</span></p>" : "") +
            "<table width='100%' cellpadding='0' cellspacing='0'><tr><td align='center' style='padding-bottom:8px;'>" +
            "<a href='" + articleUrl + "' style='display:inline-block;background:#14532d;color:#ffffff;font-weight:700;font-size:15px;" +
            "text-decoration:none;padding:15px 36px;border-radius:10px;letter-spacing:0.2px;'>Read the Article →</a>" +
            "</td></tr></table>" +
            "</td></tr>" +
            "<tr><td style='background:#f8fafc;padding:24px 40px;text-align:center;border-top:1px solid #e2e8f0;'>" +
            "<p style='margin:0 0 10px;font-size:12px;color:#94a3b8;line-height:1.6;'>" +
            "You're receiving this because you subscribed to ZimRate Blog updates.</p>" +
            "<a href='" + unsubscribeUrl + "' style='font-size:12px;color:#94a3b8;text-decoration:underline;'>Unsubscribe</a>" +
            "</td></tr>" +
            "</table></td></tr></table>" +
            "</body></html>";

        MimeMessage mime = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mime, "UTF-8");
        helper.setFrom(fromEmail, "ZimRate");
        helper.setTo(sub.getEmail());
        helper.setSubject("New on ZimRate: " + post.getTitle());
        helper.setText(html, true);
        mailSender.send(mime);
    }

    // ── Manual newsletter (admin compose & send) ───────────────────────────────

    public int sendManualNewsletter(String subject, String bodyHtml) {
        List<NewsletterSubscriber> subscribers = subscriberRepository.findByActiveTrue();
        int sent = 0;
        for (NewsletterSubscriber sub : subscribers) {
            try {
                String html = wrapInEmailTemplate(bodyHtml, sub.getToken());
                MimeMessage mime = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(mime, "UTF-8");
                helper.setFrom(fromEmail, "ZimRate");
                helper.setTo(sub.getEmail());
                helper.setSubject(subject);
                helper.setText(html, true);
                mailSender.send(mime);
                sent++;
            } catch (Exception e) {
                log.error("Failed to send manual newsletter to {}: {}", sub.getEmail(), e.getMessage());
            }
        }
        return sent;
    }

    public void sendTestEmail(String subject, String bodyHtml, String toEmail) throws Exception {
        String html = wrapInEmailTemplate(bodyHtml, null);
        MimeMessage mime = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mime, "UTF-8");
        helper.setFrom(fromEmail, "ZimRate");
        helper.setTo(toEmail);
        helper.setSubject("[TEST] " + subject);
        helper.setText(html, true);
        mailSender.send(mime);
    }

    public String previewHtml(String subject, String bodyHtml) {
        return wrapInEmailTemplate(bodyHtml, null);
    }

    private String wrapInEmailTemplate(String bodyHtml, String unsubscribeToken) {
        String footerContent = unsubscribeToken != null
            ? "<a href='" + baseUrl + "/unsubscribe/newsletter/" + unsubscribeToken + "' " +
              "style='font-size:12px;color:#94a3b8;text-decoration:underline;'>Unsubscribe</a>"
            : "<span style='font-size:12px;color:#94a3b8;'>[Test email — no unsubscribe link]</span>";

        return "<!DOCTYPE html>" +
            "<html lang='en'><head><meta charset='UTF-8'>" +
            "<meta name='viewport' content='width=device-width,initial-scale=1'></head>" +
            "<body style='margin:0;padding:0;background:#f1f5f9;font-family:Inter,Arial,sans-serif;'>" +
            "<table width='100%' cellpadding='0' cellspacing='0' style='background:#f1f5f9;padding:40px 16px;'><tr><td align='center'>" +
            "<table width='560' cellpadding='0' cellspacing='0' style='background:#ffffff;border-radius:16px;overflow:hidden;box-shadow:0 4px 24px rgba(0,0,0,0.08);'>" +
            "<tr><td style='background:linear-gradient(135deg,#14532d 0%,#166534 100%);padding:36px 40px;text-align:center;'>" +
            "<div style='font-size:32px;font-weight:800;letter-spacing:-1px;'>" +
            "<span style='color:#ffffff;'>Zim</span><span style='color:#ca8a04;'>Rate</span></div>" +
            "<p style='margin:8px 0 0;color:rgba(255,255,255,0.7);font-size:13px;letter-spacing:0.3px;'>Zimbabwe Forex Rates</p>" +
            "</td></tr>" +
            "<tr><td style='padding:36px 40px;font-size:14px;color:#334155;line-height:1.7;'>" +
            bodyHtml +
            "</td></tr>" +
            "<tr><td style='background:#f8fafc;padding:24px 40px;text-align:center;border-top:1px solid #e2e8f0;'>" +
            "<p style='margin:0 0 10px;font-size:12px;color:#94a3b8;line-height:1.6;'>" +
            "You're receiving this because you subscribed to ZimRate Blog updates.</p>" +
            footerContent +
            "</td></tr>" +
            "</table></td></tr></table>" +
            "</body></html>";
    }

    private static String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
