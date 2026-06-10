package com.forexzim.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Public developer API documentation page.
 */
@Controller
public class ApiDocsController {

    @GetMapping("/api-docs")
    public String apiDocs() {
        return "api-docs";
    }
}
