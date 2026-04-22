package com.example.mutexa_be.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {

    /**
     * Menyediakan instance ObjectMapper secara global.
     * Bean ini digunakan untuk proses konversi (parsing) JSON ke Java Object.
     */
    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        // Konfigurasi agar tidak error jika ada properti JSON yang tidak kita daftarkan di Java Class
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return mapper;
    }
}