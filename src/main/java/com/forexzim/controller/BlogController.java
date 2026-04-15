package com.forexzim.controller;

import com.forexzim.model.BlogPost;
import com.forexzim.repository.BlogRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@Controller
@RequestMapping("/blog")
public class BlogController {

    private static final DateTimeFormatter JSON_LD_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss", Locale.ENGLISH);

    private final BlogRepository blogRepository;

    @Value("${zimrate.base-url:https://zimrate.com}")
    private String baseUrl;

    public BlogController(BlogRepository blogRepository) {
        this.blogRepository = blogRepository;
    }

    @GetMapping
    public String list(Model model) {
        List<BlogPost> posts = blogRepository.findByStatusOrderByPublishedAtDesc(BlogPost.Status.PUBLISHED);
        model.addAttribute("posts", posts);
        model.addAttribute("structuredData",
            "{\"@context\":\"https://schema.org\",\"@type\":\"Blog\","
            + "\"name\":\"ZimRate Blog\","
            + "\"description\":\"Expert insights on Zimbabwe's forex market, USD/ZiG rates, and financial news.\","
            + "\"url\":\"" + baseUrl + "/blog\","
            + "\"publisher\":{\"@type\":\"Organization\",\"name\":\"ZimRate\",\"url\":\"" + baseUrl + "\"}}");
        return "blog-list";
    }

    @GetMapping("/{slug}")
    public String post(@PathVariable String slug, Model model) {
        BlogPost post = blogRepository.findBySlugAndStatus(slug, BlogPost.Status.PUBLISHED)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        model.addAttribute("post", post);
        model.addAttribute("structuredData", buildPostJsonLd(post));
        model.addAttribute("breadcrumbData", buildBreadcrumbJsonLd(post));
        return "blog-post";
    }

    private String buildBreadcrumbJsonLd(BlogPost post) {
        String title = post.getTitle().replace("\"", "\\\"");
        return "{\"@context\":\"https://schema.org\",\"@type\":\"BreadcrumbList\","
            + "\"itemListElement\":["
            + "{\"@type\":\"ListItem\",\"position\":1,\"name\":\"Home\",\"item\":\"" + baseUrl + "\"},"
            + "{\"@type\":\"ListItem\",\"position\":2,\"name\":\"Blog\",\"item\":\"" + baseUrl + "/blog\"},"
            + "{\"@type\":\"ListItem\",\"position\":3,\"name\":\"" + title + "\",\"item\":\"" + baseUrl + "/blog/" + post.getSlug() + "\"}"
            + "]}";
    }

    private String buildPostJsonLd(BlogPost post) {
        String publishedIso = post.getPublishedAt() != null
                ? post.getPublishedAt().format(JSON_LD_FMT)
                : post.getCreatedAt().format(JSON_LD_FMT);
        String modifiedIso  = post.getUpdatedAt().format(JSON_LD_FMT);
        String url          = baseUrl + "/blog/" + post.getSlug();
        String description  = post.getMetaDescription() != null
                ? post.getMetaDescription().replace("\"", "\\\"")
                : "";
        String title        = post.getTitle().replace("\"", "\\\"");

        return "{\"@context\":\"https://schema.org\",\"@type\":\"BlogPosting\","
            + "\"headline\":\"" + title + "\","
            + "\"description\":\"" + description + "\","
            + "\"author\":{\"@type\":\"Organization\",\"name\":\"" + post.getAuthor() + "\"},"
            + "\"datePublished\":\"" + publishedIso + "\","
            + "\"dateModified\":\"" + modifiedIso + "\","
            + "\"publisher\":{\"@type\":\"Organization\",\"name\":\"ZimRate\",\"url\":\"" + baseUrl + "\"},"
            + "\"mainEntityOfPage\":{\"@type\":\"WebPage\",\"@id\":\"" + url + "\"}}";
    }
}
