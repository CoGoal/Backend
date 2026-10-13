package edu.stankin.cogoalauth.kafka.config;
import jakarta.validation.constraints.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;
import java.time.Duration;

@Validated @ConfigurationProperties("app.kafka")
public record ProfileKafkaProperties(@DefaultValue("cogoal.user-profiles.v1") @NotBlank String profileTopic,
        @DefaultValue("15s") @NotNull Duration sendTimeout, @DefaultValue("3") @Positive int partitions) {}
