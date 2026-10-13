package edu.stankin.cogoalauth.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Password hashing (BCrypt) with a constant-time answer for unknown users.
 */
@Service
public class PasswordService {

    private final PasswordEncoder passwordEncoder;

    // Checked instead of a real hash when there is no user, so "no such email" takes as long as "wrong password"
    private final String dummyHash;

    public PasswordService(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
        this.dummyHash = passwordEncoder.encode("dummy-password-for-timing");
    }

    public String hash(String rawPassword) {
        return passwordEncoder.encode(rawPassword);
    }

    /**
     * @param hash the stored hash, or {@code null} when the user does not exist (then the answer is {@code false})
     */
    public boolean matches(String rawPassword, String hash) {
        if (hash == null) {
            passwordEncoder.matches(rawPassword, dummyHash);
            return false;
        }
        return passwordEncoder.matches(rawPassword, hash);
    }
}
