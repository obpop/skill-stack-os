package com.skillstack.user;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {
    private final AppUserRepository users;

    public UserService(AppUserRepository users) {
        this.users = users;
    }

    @Transactional
    public AppUser ensureUser(String subject, String suggestedName) {
        return users.findBySubject(subject).orElseGet(() -> users.save(new AppUser(subject, cleanName(suggestedName, subject))));
    }

    @Transactional(readOnly = true)
    public AppUser require(String subject) {
        return users.findBySubject(subject).orElseThrow(() -> new IllegalStateException("Authenticated user has not been provisioned"));
    }

    @Transactional
    public AppUser completeOnboarding(String subject) {
        var user = require(subject);
        user.completeOnboarding();
        return users.saveAndFlush(user);
    }

    @Transactional
    public AppUser updateDefaultDomain(String subject, String defaultDomain) {
        var user = require(subject);
        user.updateDefaultDomain(defaultDomain);
        return users.saveAndFlush(user);
    }

    @Transactional
    public void deleteAccount(String subject) {
        var user = require(subject);
        users.delete(user);
        users.flush();
    }

    private String cleanName(String value, String fallback) {
        if (value == null || value.isBlank()) return fallback.substring(0, Math.min(120, fallback.length()));
        return value.trim().substring(0, Math.min(120, value.trim().length()));
    }
}
