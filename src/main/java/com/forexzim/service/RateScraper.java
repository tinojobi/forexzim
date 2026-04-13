package com.forexzim.service;

import com.forexzim.model.Rate;
import com.forexzim.model.Source;

import java.util.List;

public interface RateScraper {
    List<Rate> scrape(Source source);
}