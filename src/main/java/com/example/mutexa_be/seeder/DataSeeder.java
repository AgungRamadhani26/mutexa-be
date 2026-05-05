package com.example.mutexa_be.seeder;

import com.example.mutexa_be.entity.User;
import com.example.mutexa_be.entity.enums.UserRole;
import com.example.mutexa_be.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

   private final UserRepository userRepository;
   private final BCryptPasswordEncoder passwordEncoder;

   @Override
   public void run(String... args) {
      seedAdminUser();
   }

   private void seedAdminUser() {
      String adminEmail = "admin@mutexa.com";

      if (userRepository.existsByEmail(adminEmail)) {
         log.info("[DataSeeder] Admin user sudah ada, skip seeding.");
         return;
      }

      User admin = User.builder()
            .name("Administrator")
            .email(adminEmail)
            .password(passwordEncoder.encode("admin123"))
            .role(UserRole.ADMIN)
            .isActive(true)
            .build();

      userRepository.save(admin);
      log.info("[DataSeeder] ✅ Admin user berhasil dibuat: {}", adminEmail);
      log.info("[DataSeeder] ⚠️  Harap ganti password default setelah login pertama!");
   }
}
