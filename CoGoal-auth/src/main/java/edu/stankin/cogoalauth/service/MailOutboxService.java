package edu.stankin.cogoalauth.service;

import edu.stankin.cogoalauth.config.security.JwtProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;

/** Durable SMTP delivery; action links are encrypted at rest and never written to logs. */
@Service
public class MailOutboxService {
    private final JdbcTemplate jdbc;
    private final JavaMailSender sender;
    private final Clock clock;
    private final SecretKeySpec key;
    private final String from;
    private final String publicUrl;
    public MailOutboxService(JdbcTemplate jdbc, JavaMailSender sender, Clock clock, JwtProperties jwt,
            @Value("${app.mail.from}") String from, @Value("${app.mail.public-base-url}") String publicUrl) {
        this.jdbc=jdbc; this.sender=sender; this.clock=clock; this.from=from; this.publicUrl=publicUrl;
        try { this.key = new SecretKeySpec(MessageDigest.getInstance("SHA-256").digest(("mail-outbox:"+jwt.secret()).getBytes(StandardCharsets.UTF_8)), "AES"); }
        catch (GeneralSecurityException e) { throw new IllegalStateException(e); }
    }
    public void enqueue(String to, String purpose, String token, Instant expires) {
        String route = purpose.equals("PASSWORD_RESET") ? "/reset-password" : "/email-confirm";
        String body = "Ссылка CoGoal действует 30 минут:\n" + publicUrl + route + "?token=" + token;
        jdbc.update("INSERT INTO mail_outbox(id,recipient,body_encrypted,expires_at,next_attempt_at,attempts) VALUES(?,?,?,?,?,0)",
                UUID.randomUUID(), to, encrypt(body), Timestamp.from(expires), Timestamp.from(clock.instant()));
    }
    @Scheduled(fixedDelayString="${app.mail.poll-interval:5s}")
    @Transactional
    public void deliver() {
        Instant now = clock.instant();
        jdbc.update("DELETE FROM mail_outbox WHERE expires_at<=?", Timestamp.from(now));
        var pending = jdbc.query("SELECT id,recipient,body_encrypted FROM mail_outbox WHERE next_attempt_at<=? ORDER BY next_attempt_at LIMIT 10 FOR UPDATE SKIP LOCKED",
                (rs, row) -> new Pending(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3)), Timestamp.from(now));
        for (Pending mail : pending) {
            try {
                SimpleMailMessage message = new SimpleMailMessage();
                message.setFrom(from); message.setTo(mail.to()); message.setSubject("CoGoal: подтверждение действия"); message.setText(decrypt(mail.body()));
                sender.send(message);
                jdbc.update("DELETE FROM mail_outbox WHERE id=?", mail.id());
            } catch (RuntimeException e) {
                jdbc.update("UPDATE mail_outbox SET attempts=attempts+1,next_attempt_at=? WHERE id=?", Timestamp.from(now.plusSeconds(60)), mail.id());
            }
        }
    }
    private String encrypt(String body) {
        try {
            byte[] iv = new byte[12]; new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
            byte[] encoded = cipher.doFinal(body.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(iv)+":"+Base64.getEncoder().encodeToString(encoded);
        } catch (GeneralSecurityException e) { throw new IllegalStateException(e); }
    }
    private String decrypt(String body) {
        try {
            String[] parts = body.split(":",2);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, Base64.getDecoder().decode(parts[0])));
            return new String(cipher.doFinal(Base64.getDecoder().decode(parts[1])), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) { throw new IllegalStateException(e); }
    }
    private record Pending(UUID id, String to, String body) {}
}
