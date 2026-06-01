package com.bowon.cpm.news.util;

import org.springframework.web.util.HtmlUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * 뉴스 처리 유틸리티
 */
public final class NewsUtils {

    private NewsUtils() {
    }

    /**
     * HTML 태그 및 엔티티 제거
     * 네이버 뉴스 title/description에 &lt;b&gt; 등 HTML이 포함됨
     */
    public static String cleanHtml(String value) {
        if (value == null) return null;
        String unescaped = HtmlUtils.htmlUnescape(value);
        return unescaped.replaceAll("<[^>]*>", "").trim();
    }

    /**
     * URL의 SHA-256 해시 생성 — stock_news 중복 제거용 UK
     * origin_url은 길이 제한으로 직접 UK 불가, 해시로 대체
     */
    public static String sha256(String value) {
        if (value == null) {
            throw new IllegalArgumentException("hash 대상 값이 null");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encoded = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(encoded);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 생성 실패", e);
        }
    }
}

