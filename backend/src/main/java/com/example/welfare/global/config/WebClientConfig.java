package com.example.welfare.global.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.codec.xml.Jaxb2XmlDecoder;
import org.springframework.http.codec.xml.Jaxb2XmlEncoder;
import org.springframework.http.converter.xml.MappingJackson2XmlHttpMessageConverter;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

import javax.xml.XMLConstants;
import javax.xml.stream.XMLInputFactory;

@Configuration
public class WebClientConfig {

    /**
     * XXE(XML External Entity) 공격 방지를 위해 XMLInputFactory 비활성화 후 XmlMapper 생성
     */
    @Bean
    public XmlMapper xmlMapper() {
        XMLInputFactory xmlInputFactory = XMLInputFactory.newInstance();
        // XXE 비활성화 — CLAUDE.md 필수 요구사항
        xmlInputFactory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.FALSE);
        xmlInputFactory.setProperty(XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);

        XmlMapper mapper = new XmlMapper(xmlInputFactory);
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return mapper;
    }

    @Bean
    public WebClient webClient() {
        // 응답 버퍼 크기 제한 완화 (대용량 API 응답 대비: 10MB)
        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(config -> config.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                .build();

        return WebClient.builder()
                .exchangeStrategies(strategies)
                .build();
    }
}
