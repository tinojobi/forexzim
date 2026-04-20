package com.forexzim.controller;

import com.forexzim.dto.WidgetRateResponse;
import com.forexzim.repository.RateRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.math.RoundingMode;
import java.util.List;
import java.util.stream.Collectors;

@Controller
public class WidgetController {

    private final RateRepository rateRepository;

    @Value("${zimrate.base-url:https://zimrate.com}")
    private String baseUrl;

    public WidgetController(RateRepository rateRepository) {
        this.rateRepository = rateRepository;
    }

    @GetMapping(value = "/widget.js", produces = "application/javascript")
    @ResponseBody
    public ResponseEntity<String> widgetJs() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=300")
                .header(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "*")
                .body(buildJs());
    }

    @GetMapping(value = "/api/widget/rates", produces = "application/json")
    @ResponseBody
    @CrossOrigin(origins = "*")
    public List<WidgetRateResponse> widgetRates() {
        return rateRepository.findLatestBySourceAndCurrencyPair().stream()
                .filter(r -> r.getCurrencyPair() != null
                        && (r.getCurrencyPair().equalsIgnoreCase("USD/ZiG")
                         || r.getCurrencyPair().equalsIgnoreCase("USD/ZWG")))
                .map(r -> new WidgetRateResponse(
                        r.getSource().getName(),
                        r.getSource().getType() != null ? r.getSource().getType() : "",
                        r.getBuyRate()  != null ? r.getBuyRate().setScale(2, RoundingMode.HALF_UP).toPlainString() : "-",
                        r.getSellRate() != null ? r.getSellRate().setScale(2, RoundingMode.HALF_UP).toPlainString() : "-"
                ))
                .collect(Collectors.toList());
    }

    @GetMapping("/widget")
    public String widgetPage(Model model) {
        model.addAttribute("embedCode",
                "<script src=\"" + baseUrl + "/widget.js\"></script>");
        return "widget";
    }

    private String buildJs() {
        // CSS uses double-quotes for font names so it embeds safely inside a JS single-quoted string
        String css =
                ".zr-w{font-family:-apple-system,BlinkMacSystemFont,\"Segoe UI\",sans-serif;font-size:14px;"
                + "line-height:1.4;border:1.5px solid #d1fae5;border-radius:12px;overflow:hidden;"
                + "max-width:300px;background:#fff;color:#111;box-shadow:0 2px 8px rgba(0,0,0,.08);margin:8px 0}"
                + ".zr-hd{display:flex;justify-content:space-between;align-items:center;"
                + "padding:10px 14px;background:#15803d;color:#fff;text-decoration:none}"
                + ".zr-title{font-weight:700;font-size:13px;letter-spacing:.3px;color:#fff}"
                + ".zr-brand{font-size:11px;color:#bbf7d0;font-weight:600}"
                + ".zr-hd:hover .zr-brand{color:#fff}"
                + ".zr-t{width:100%;border-collapse:collapse}"
                + ".zr-t th{background:#f0fdf4;color:#166534;font-size:11px;font-weight:700;"
                + "text-transform:uppercase;letter-spacing:.5px;padding:5px 14px;text-align:left}"
                + ".zr-t td{padding:7px 14px;border-top:1px solid #f3f4f6;font-size:13px}"
                + ".zr-t td:first-child{color:#374151;font-size:12px}"
                + ".zr-t td:not(:first-child){font-weight:600;font-variant-numeric:tabular-nums;color:#111}"
                + ".zr-ft{display:block;padding:7px 14px;background:#f9fafb;border-top:1px solid #e5e7eb;"
                + "font-size:11px;color:#15803d;text-decoration:none;text-align:center;font-weight:500}"
                + ".zr-ft:hover{text-decoration:underline}"
                + ".zr-loading,.zr-err{padding:12px 14px;color:#6b7280;font-size:12px;margin:0}"
                + ".zr-err a{color:#15803d}";

        return "(function(){\n"
                + "'use strict';\n"
                + "var BASE='" + baseUrl + "';\n"
                + "var P='zr';\n"
                + "function injectStyles(){\n"
                + "  if(document.getElementById(P+'-css'))return;\n"
                + "  var s=document.createElement('style');\n"
                + "  s.id=P+'-css';\n"
                + "  s.textContent='" + css + "';\n"
                + "  document.head.appendChild(s);\n"
                + "}\n"
                + "function makeWidget(){\n"
                + "  var d=document.createElement('div');\n"
                + "  d.className=P+'-w';\n"
                + "  d.innerHTML='<div class=\"'+P+'-loading\">Loading\u2026</div>';\n"
                + "  var ss=document.getElementsByTagName('script');\n"
                + "  var me=ss[ss.length-1];\n"
                + "  me.parentNode.insertBefore(d,me.nextSibling);\n"
                + "  return d;\n"
                + "}\n"
                + "function esc(s){return String(s).replace(/&/g,'&amp;').replace(/</g,'&lt;');}\n"
                + "function render(w,data){\n"
                + "  if(!data||!data.length){w.innerHTML='<div class=\"'+P+'-err\">No rates available</div>';return;}\n"
                + "  var rows=data.map(function(r){\n"
                + "    return '<tr><td>'+esc(r.source)+'</td><td>'+r.buy+'</td><td>'+r.sell+'</td></tr>';\n"
                + "  }).join('');\n"
                + "  w.innerHTML=\n"
                + "    '<a class=\"'+P+'-hd\" href=\"'+BASE+'\" target=\"_blank\" rel=\"noopener\">'+\n"
                + "      '<span class=\"'+P+'-title\">USD / ZiG</span>'+\n"
                + "      '<span class=\"'+P+'-brand\">ZimRate</span>'+\n"
                + "    '</a>'+\n"
                + "    '<table class=\"'+P+'-t\">'+\n"
                + "      '<thead><tr><th>Source</th><th>Buy</th><th>Sell</th></tr></thead>'+\n"
                + "      '<tbody>'+rows+'</tbody>'+\n"
                + "    '</table>'+\n"
                + "    '<a class=\"'+P+'-ft\" href=\"'+BASE+'\" target=\"_blank\" rel=\"noopener\">Live rates at zimrate.com \u2192</a>';\n"
                + "}\n"
                + "injectStyles();\n"
                + "var widget=makeWidget();\n"
                + "fetch(BASE+'/api/widget/rates')\n"
                + "  .then(function(r){return r.json();})\n"
                + "  .then(function(d){render(widget,d);})\n"
                + "  .catch(function(){\n"
                + "    widget.innerHTML='<div class=\"'+P+'-err\">Could not load rates. "
                + "<a href=\"'+BASE+'\" target=\"_blank\" rel=\"noopener\">Visit ZimRate</a></div>';\n"
                + "  });\n"
                + "})();\n";
    }
}
