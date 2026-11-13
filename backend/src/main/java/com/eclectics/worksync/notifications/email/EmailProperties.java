package com.eclectics.worksync.notifications.email;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "worksync.email")
public record EmailProperties(@NotBlank @Email @Size(max = 254) String from) {
}
