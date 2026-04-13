package com.forexzim.service;

import com.forexzim.Rate;
import com.forexzim.Source;

import java.util.List;

public interface RateScraper {
    List<Rate> scrape(Source source);
}