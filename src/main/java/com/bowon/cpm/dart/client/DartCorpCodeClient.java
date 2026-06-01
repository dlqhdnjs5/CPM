package com.bowon.cpm.dart.client;

import com.bowon.cpm.dart.domain.DartCorpCode;
import com.bowon.cpm.common.exception.ExternalApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * DART corpCode.xml 다운로드 클라이언트
 *
 * API: GET /api/corpCode.xml?crtfc_key={apiKey}
 * 응답: ZIP 파일 (내부에 CORPCODE.xml 포함)
 *
 * XML 구조:
 * <result>
 *   <list>
 *     <corp_code>00126380</corp_code>
 *     <corp_name>삼성전자</corp_name>
 *     <stock_code>005930</stock_code>
 *     <modify_date>20240101</modify_date>
 *   </list>
 * </result>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DartCorpCodeClient {

    private final WebClient dartWebClient;

    @Value("${external.dart.api-key}")
    private String apiKey;

    /**
     * DART corp_code 전체 목록 다운로드 + 파싱
     * 상장 종목(stock_code 있는 것)만 반환
     */
    public List<DartCorpCode> fetchCorpCodes() {
        log.info("[DART] corpCode.xml 다운로드 시작");

        byte[] zipBytes = dartWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/corpCode.xml")
                        .queryParam("crtfc_key", apiKey)
                        .build())
                .retrieve()
                .bodyToMono(byte[].class)
                .block();

        if (zipBytes == null || zipBytes.length == 0) {
            throw new ExternalApiException("DART", "corpCode.xml 다운로드 실패: 응답 없음");
        }

        List<DartCorpCode> result = parseZip(zipBytes);
        log.info("[DART] corpCode 파싱 완료: total={}", result.size());
        return result;
    }

    private List<DartCorpCode> parseZip(byte[] zipBytes) {
        try (ZipInputStream zis = new ZipInputStream(
                new ByteArrayInputStream(zipBytes), StandardCharsets.UTF_8)) {

            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().toLowerCase().endsWith(".xml")) {
                    byte[] xmlBytes = zis.readAllBytes();
                    return parseXml(xmlBytes);
                }
            }
        } catch (Exception e) {
            throw new ExternalApiException("DART", "corpCode.xml ZIP 파싱 실패: " + e.getMessage());
        }
        throw new ExternalApiException("DART", "ZIP 내 XML 파일 없음");
    }

    private List<DartCorpCode> parseXml(byte[] xmlBytes) {
        List<DartCorpCode> list = new ArrayList<>();
        try {
            Document doc = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder()
                    .parse(new ByteArrayInputStream(xmlBytes));

            NodeList nodes = doc.getElementsByTagName("list");
            for (int i = 0; i < nodes.getLength(); i++) {
                Element el = (Element) nodes.item(i);
                String corpCode = getTagValue(el, "corp_code");
                String corpName = getTagValue(el, "corp_name");
                String stockCode = getTagValue(el, "stock_code");
                String modifyDateStr = getTagValue(el, "modify_date");

                LocalDate modifyDate = null;
                if (modifyDateStr != null && modifyDateStr.length() == 8) {
                    modifyDate = LocalDate.parse(modifyDateStr,
                            java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
                }

                list.add(DartCorpCode.builder()
                        .corpCode(corpCode)
                        .corpName(corpName)
                        .stockCode(stockCode != null && !stockCode.isBlank() ? stockCode : null)
                        .modifyDate(modifyDate)
                        .build());
            }
        } catch (Exception e) {
            throw new ExternalApiException("DART", "corpCode XML 파싱 실패: " + e.getMessage());
        }
        return list;
    }

    private String getTagValue(Element el, String tagName) {
        NodeList nodes = el.getElementsByTagName(tagName);
        if (nodes.getLength() == 0) return null;
        String value = nodes.item(0).getTextContent();
        return value != null ? value.trim() : null;
    }
}

