package com.untitles.domain.email;

import com.untitles.domain.email.service.EmailService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class EmailServiceTest {

    @Autowired
    private EmailService emailService;

    private static final String TEST_EMAIL = "gokid360@gmail.com";

    @Test
    @DisplayName("이메일 중복 확인 - 미가입 이메일")
    void 이메일_중복확인_미가입() {
        // given
        String newEmail = "newuser@test.com";

        // when
        boolean isDuplicate = emailService.checkDuplicate(newEmail);

        // then
        assertThat(isDuplicate).isFalse();
    }

    @Test
    @DisplayName("인증번호 발송 테스트 - 실제 이메일 발송")
    void 인증번호_발송_테스트() {
        // given & when
        // SES Sandbox 모드: 인증된 이메일로만 발송 가능
        emailService.sendVerificationCode(TEST_EMAIL);

        // then
        System.out.println("인증번호 발송 완료! 메일함 확인해보세요.");
        
        // 인증 데이터가 저장되었는지 확인
        boolean isVerified = emailService.isVerified(TEST_EMAIL);
        assertThat(isVerified).isFalse(); // 아직 인증 안 함
    }

    @Test
    @DisplayName("인증번호 검증 - 잘못된 코드")
    void 인증번호_검증_실패() {
        // given
        emailService.sendVerificationCode(TEST_EMAIL);

        // when & then
        assertThatThrownBy(() -> emailService.verifyCode(TEST_EMAIL, "000000"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("인증번호가 일치하지 않습니다.");
    }

    @Test
    @DisplayName("인증 요청 없이 검증 시도")
    void 인증요청_없이_검증() {
        // given
        String email = "noauth@test.com";

        // when & then
        assertThatThrownBy(() -> emailService.verifyCode(email, "123456"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("인증 요청을 먼저 해주세요.");
    }
}
