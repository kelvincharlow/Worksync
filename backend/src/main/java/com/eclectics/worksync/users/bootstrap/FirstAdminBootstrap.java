package com.eclectics.worksync.users.bootstrap;

import com.eclectics.worksync.auth.config.PasswordEncodingConfiguration;
import com.eclectics.worksync.users.entity.User;
import com.eclectics.worksync.users.repository.UserRepository;
import java.io.Console;
import java.util.Arrays;
import java.util.Map;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.transaction.autoconfigure.TransactionAutoConfiguration;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/** Separate, interactive entry point. Never invoked by WorksyncApplication or a startup runner. */
public final class FirstAdminBootstrap {
    private FirstAdminBootstrap() {}

    public static void main(String[] args) {
        int exit = run(args, System.console(), System.getenv());
        if (exit != 0) System.exit(exit);
    }

    static int run(String[] args, Console console, Map<String, String> environment) {
        // Reject before creating any context or opening any database connection.
        if (args.length != 1 || !"--create-first-admin".equals(args[0]) || console == null) {
            System.err.println("Requires --create-first-admin and an interactive terminal; no changes made");
            return 2;
        }
        for (String key : new String[]{"DB_URL", "POSTGRES_USER", "POSTGRES_PASSWORD"}) {
            if (environment.get(key) == null || environment.get(key).isBlank()) {
                console.printf("Required database environment is missing; no changes made%n");
                return 2;
            }
        }
        console.printf("Confirm this shell's DB_URL points to the intended database. No connection details will be printed.%n");
        if (!"CREATE FIRST ADMIN".equals(console.readLine("Type CREATE FIRST ADMIN to continue: "))) {
            console.printf("Cancelled; no changes made%n");
            return 2;
        }
        char[] password = null;
        char[] confirmation = null;
        try {
            String firstName = readHidden(console, "First name: ");
            String lastName = readHidden(console, "Last name: ");
            String email = readHidden(console, "Email: ");
            String phone = readHidden(console, "Phone (E.164): ");
            password = console.readPassword("Password (12+ characters, maximum 72 UTF-8 bytes): ");
            confirmation = console.readPassword("Confirm password: ");
            if (password == null || confirmation == null || !Arrays.equals(password, confirmation)) {
                console.printf("Passwords did not match; no changes made%n");
                return 2;
            }
            SpringApplication application = new SpringApplication(BootstrapConfiguration.class);
            application.setWebApplicationType(WebApplicationType.NONE);
            application.setBannerMode(Banner.Mode.OFF);
            application.setLogStartupInfo(false);
            application.setAddCommandLineProperties(false);
            var isolatedEnvironment = new StandardEnvironment();
            // Do not inherit arbitrary debug/logging/profile overrides that might log SQL binds.
            isolatedEnvironment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
            isolatedEnvironment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
            isolatedEnvironment.getPropertySources().addFirst(
                    new MapPropertySource("operator-bootstrap", Map.ofEntries(
                            Map.entry("spring.datasource.url", environment.get("DB_URL")),
                            Map.entry("spring.datasource.username", environment.get("POSTGRES_USER")),
                            Map.entry("spring.datasource.password", environment.get("POSTGRES_PASSWORD")),
                            Map.entry("spring.config.location", "optional:classpath:worksync-operator-bootstrap.properties"),
                            Map.entry("spring.profiles.active", ""),
                            Map.entry("spring.profiles.include", ""),
                            Map.entry("spring.jpa.hibernate.ddl-auto", "validate"),
                            Map.entry("spring.jpa.show-sql", "false"),
                            Map.entry("spring.main.log-startup-info", "false"),
                            Map.entry("logging.level.root", "OFF"),
                            Map.entry("logging.level.org.hibernate", "OFF"),
                            Map.entry("logging.level.org.springframework", "OFF"),
                            Map.entry("logging.level.com.zaxxer.hikari", "OFF"),
                            Map.entry("logging.level.org.postgresql", "OFF"))));
            application.setEnvironment(isolatedEnvironment);
            try (var context = application.run()) {
                context.getBean(FirstAdminBootstrapService.class).create(firstName, lastName, email, phone, password);
            }
            console.printf("First ACTIVE ADMIN committed successfully. No credentials are displayed.%n");
            return 0;
        } catch (Exception failure) {
            // Provider/validation exceptions may contain passwords, hashes or database URLs.
            // Deliberately do not print the exception, cause, SQL or rejected values.
            console.printf("Bootstrap failed or was refused. Do not retry blindly; verify existing administrator state through an approved procedure.%n");
            return 1;
        } finally {
            if (password != null) Arrays.fill(password, '\0');
            if (confirmation != null) Arrays.fill(confirmation, '\0');
        }
    }

    private static String readHidden(Console console, String prompt) {
        char[] value = console.readPassword(prompt);
        if (value == null) throw new IllegalArgumentException("Input cancelled");
        try {
            return new String(value);
        } finally {
            Arrays.fill(value, '\0');
        }
    }

    // Deliberately NOT @Configuration/@Component: normal application scanning must not enable it.
    @ImportAutoConfiguration({DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class,
            TransactionAutoConfiguration.class, ValidationAutoConfiguration.class})
    @EnableTransactionManagement
    @EntityScan(basePackageClasses = User.class)
    @EnableJpaRepositories(basePackageClasses = UserRepository.class)
    @Import({FirstAdminBootstrapService.class, PasswordEncodingConfiguration.class})
    public static class BootstrapConfiguration {
    }
}
