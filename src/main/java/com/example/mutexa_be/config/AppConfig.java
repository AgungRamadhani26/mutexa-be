package com.example.mutexa_be.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {

    /**
     * Menyediakan instance ObjectMapper secara global.
     * Bean ini diperlukan oleh OllamaOcrService untuk proses konversi (parsing)
     * Json Text ke Object Java, maupun Object Java ke Json Text.
     */
    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        // Konfigurasi agar tidak error jika ada properti JSON yang tidak kita daftarkan di Java Class
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return mapper;
    }
}