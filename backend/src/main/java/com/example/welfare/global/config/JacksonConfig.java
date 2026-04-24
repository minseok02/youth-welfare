package com.example.welfare.global.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
public class JacksonConfig implements WebMvcConfigurer {

    /**
     * JSON을 기본 ObjectMapper로 설정
     * jackson-dataformat-xml 의존성이 있어도 REST API는 JSON으로 동작
     */
    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }

    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        // 기존 MappingJackson2HttpMessageConverter의 ObjectMapper를 커스텀으로 교체
        // 새 인스턴스를 add하면 byte[] 응답(springdoc /v3/api-docs)을 Jackson이 가로채
        // Base64로 직렬화하는 문제가 생기므로, 기존 인스턴스의 mapper만 교체
        for (HttpMessageConverter<?> converter : converters) {
            if (converter instanceof MappingJackson2HttpMessageConverter jacksonConverter) {
                jacksonConverter.setObjectMapper(objectMapper());
                break;
            }
        }
    }
}
