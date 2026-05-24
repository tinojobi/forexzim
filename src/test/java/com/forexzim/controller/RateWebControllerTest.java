package com.forexzim.controller;

import com.forexzim.repository.BlogRepository;
import com.forexzim.service.GoldCoinService;
import com.forexzim.service.InflationScraperService;
import com.forexzim.service.RateService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RateController.WebController.class)
class RateWebControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RateService rateService;

    @MockitoBean
    private GoldCoinService goldCoinService;

    @MockitoBean
    private InflationScraperService inflationScraperService;

    @MockitoBean
    private BlogRepository blogRepository;

    @Test
    void convertIndexRedirectsToDefaultConversionPage() throws Exception {
        mockMvc.perform(get("/convert"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("https://zimrate.com/convert/100-usd-to-zig"));
    }
}
