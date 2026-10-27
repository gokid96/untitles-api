package com.untitles.domain.invitation.entity;

import com.untitles.domain.workspace.entity.WorkspaceRole;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;

/**
 * 워크스페이스 초대 (깃허브 방식)
 * - 초대받은 사람이 수락해야 멤버가 된다. 거절·취소·수락되면 지운다 (대기 중인 초대만 남음)
 * - 가입한 사용자는 inviteeUserId, 아직 가입하지 않은 사람은 email로 초대한다
 * - 7일이 지나면 만료
 */
@Entity
@Table(name = "workspace_invitation",
        indexes = {
                @Index(name = "idx_invitation_workspace", columnList = "workspaceId"),
                @Index(name = "idx_invitation_invitee", columnList = "inviteeUserId"),
                @Index(name = "idx_invitation_email", columnList = "email")
        },
        uniqueConstraints = @UniqueConstraint(name = "uk_invitation_token", columnNames = "token"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkspaceInvitation {

    public static final int EXPIRATION_DAYS = 7;
    private static final SecureRandom RANDOM = new SecureRandom();

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long workspaceId;

    @Column(nullable = false)
    private Long inviterId;

    private Long inviteeUserId;

    @Column(length = 255)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WorkspaceRole role;

    @Column(nullable = false, length = 64)
    private String token;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    private LocalDateTime lastSentAt;

    public static WorkspaceInvitation create(Long workspaceId, Long inviterId, Long inviteeUserId, String email,
                                             WorkspaceRole role, LocalDateTime now) {
        WorkspaceInvitation invitation = new WorkspaceInvitation();
        invitation.workspaceId = workspaceId;
        invitation.inviterId = inviterId;
        invitation.inviteeUserId = inviteeUserId;
        invitation.email = email == null ? null : email.trim().toLowerCase();
        invitation.role = role;
        invitation.token = newToken();
        invitation.createdAt = now;
        invitation.expiresAt = now.plusDays(EXPIRATION_DAYS);
        return invitation;
    }

    /** 같은 사람을 다시 초대하면 권한을 바꾸고 기한을 7일 연장한다 (깃허브의 다시 보내기) */
    public void renew(Long inviterId, WorkspaceRole role, LocalDateTime now) {
        this.inviterId = inviterId;
        this.role = role;
        this.expiresAt = now.plusDays(EXPIRATION_DAYS);
    }

    public boolean isExpired(LocalDateTime now) {
        return !expiresAt.isAfter(now);
    }

    /** 메일을 너무 자주 보내지 않도록 (1분 간격) */
    public boolean canResendMail(LocalDateTime now) {
        return lastSentAt == null || lastSentAt.plusMinutes(1).isBefore(now);
    }

    public void markSent(LocalDateTime now) {
        this.lastSentAt = now;
    }

    private static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
