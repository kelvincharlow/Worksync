package com.eclectics.worksync;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class WorksyncApplicationTests {
    @MockitoBean JavaMailSender mailSender;
    @Test
    void contextLoads() {
    }
}
