package com.forexzim.service;

import com.forexzim.model.Source;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/** Test helper for loading HTML/JSON fixtures and building Source stubs. */
final class ScraperFixtures {

    private ScraperFixtures() {
    }

    static String load(String name) {
        String path = "/fixtures/" + name;
        try (InputStream in = ScraperFixtures.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Fixture not found on classpath: " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read fixture " + path, e);
        }
    }

    static Source source(String name) {
        Source source = new Source();
        source.setId(1L);
        source.setName(name);
        source.setType("bank");
        source.setUrl("https://example.test/" + name.toLowerCase());
        source.setActive(true);
        return source;
    }
}
