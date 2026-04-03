package com.nau.animalhospital.cs.config;

import com.nau.animalhospital.cs.dao.TenantRepository;
import com.nau.animalhospital.cs.entity.Tenant;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class TenantBootstrap implements ApplicationRunner {
    private final TenantRepository tenantRepository;

    @Override
    public void run(ApplicationArguments args) {
        if (tenantRepository.count() > 0) {
            return;
        }
        Tenant t = new Tenant();
        t.setCode("default");
        t.setName("default");
        t.setCreatedAt(LocalDateTime.now());
        tenantRepository.save(t);
    }
}
