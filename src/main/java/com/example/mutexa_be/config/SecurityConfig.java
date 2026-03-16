package com.example.mutexa_be.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

   @Bean
   public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
      http
            .csrf(AbstractHttpConfigurer::disable) // Matikan perlindungan CSRF karena kita API murni
            .cors(AbstractHttpConfigurer::disable) // Matikan perlindungan CORS sementara untuk development
            .authorizeHttpRequests(auth -> auth
                  .anyRequest().permitAll() // IZINKAN SEMUA REQUEST TANPA LOGIN (Tahap 1 MVP)
            );

      return http.build();
   }
}