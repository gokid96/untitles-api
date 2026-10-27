package com.untitles.domain.invitation.service;

import com.untitles.domain.email.service.EmailSender;
import com.untitles.domain.invitation.dto.InvitationCreateRequest;
import com.untitles.domain.invitation.dto.InvitationPreview;
import com.untitles.domain.invitation.dto.InvitationResponse;
import com.untitles.domain.invitation.entity.WorkspaceInvitation;
import com.untitles.domain.invitation.repository.WorkspaceInvitationRepository;
import com.untitles.domain.user.entity.Users;
import com.untitles.domain.user.repository.UserRepository;
import com.untitles.domain.workspace.entity.Workspace;
import com.untitles.domain.workspace.entity.WorkspaceMember;
import com.untitles.domain.workspace.entity.WorkspaceRole;
import com.untitles.domain.workspace.entity.WorkspaceType;
import com.untitles.domain.workspace.repository.WorkspaceMemberRepository;
import com.untitles.domain.workspace.repository.WorkspaceRepository;
import com.untitles.global.exception.BusinessException;
import com.untitles.global.exception.ErrorCode;
import com.untitles.global.util.WorkspaceMemberHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 워크스페이스 초대 (깃허브 방식)
 * 관리자가 초대 → 초대받은 사람에게 메일·앱 알림 → 수락하면 멤버, 거절하거나 관리자가 취소하면 사라짐. 7일 후 만료.
 * 멤버 수 제한(5명)에는 대기 중인 초대도 포함한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InvitationService {

    public static final int MEMBER_LIMIT = 5;

    private final WorkspaceInvitationRepository invitationRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final WorkspaceMemberHelper memberHelper;
    private final EmailSender emailSender;

    @Value("${app.oauth2.redirect-uri:http://localhost:5173}")
    private String webBaseUrl;

    private LocalDateTime now() {
        return LocalDateTime.now();
    }

    /** 초대 (OWNER·ADMIN). 이미 초대한 사람이면 권한을 바꾸고 기한을 연장해 다시 보낸다 */
    @Transactional
    public InvitationResponse invite(Long inviterId, Long workspaceId, InvitationCreateRequest request) {
        WorkspaceMember inviter = memberHelper.getMemberOrThrow(inviterId, workspaceId);
        memberHelper.checkPermission(inviter, WorkspaceRole.ADMIN);
        if (request.role() == WorkspaceRole.OWNER) throw new BusinessException(ErrorCode.CANNOT_ASSIGN_OWNER_ROLE);

        // 동시에 여러 명을 초대해도 인원 제한을 넘지 않도록 워크스페이스 단위로 잠근다
        Workspace workspace = workspaceRepository.findByIdForUpdate(workspaceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WORKSPACE_NOT_FOUND));
        if (workspace.getType() == WorkspaceType.PERSONAL) {
            throw new BusinessException(ErrorCode.CANNOT_INVITE_TO_PERSONAL_WORKSPACE);
        }

        // 대상: 가입한 사용자면 계정으로, 아니면 이메일로
        Users invitee = null;
        String email = null;
        if (request.userId() != null) {
            invitee = userRepository.findById(request.userId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        } else {
            email = request.email().trim().toLowerCase();
            invitee = userRepository.findByEmailIgnoreCase(email).orElse(null);
        }
        if (invitee != null) {
            if (invitee.getUserId().equals(inviterId)) throw new BusinessException(ErrorCode.CANNOT_INVITE_SELF);
            if (memberRepository.findByWorkspaceWorkspaceIdAndUserUserId(workspaceId, invitee.getUserId()).isPresent()) {
                throw new BusinessException(ErrorCode.ALREADY_WORKSPACE_MEMBER);
            }
            email = invitee.getEmail();
        }

        LocalDateTime now = now();
        Optional<WorkspaceInvitation> existing = invitee != null
                ? invitationRepository.findByWorkspaceIdAndInviteeUserId(workspaceId, invitee.getUserId())
                : invitationRepository.findByWorkspaceIdAndEmail(workspaceId, email);

        WorkspaceInvitation invitation;
        if (existing.isPresent() && !existing.get().isExpired(now)) {
            invitation = existing.get();
            invitation.renew(inviterId, request.role(), now);
        } else {
            existing.ifPresent(invitationRepository::delete);
            long used = memberRepository.countByWorkspace(workspace) + invitationRepository.countPendingByWorkspace(workspaceId, now);
            if (used >= MEMBER_LIMIT) throw new BusinessException(ErrorCode.MEMBER_LIMIT_EXCEEDED);
            invitation = invitationRepository.save(WorkspaceInvitation.create(
                    workspaceId, inviterId, invitee != null ? invitee.getUserId() : null,
                    invitee != null ? null : email, request.role(), now));
        }

        if (StringUtils.hasText(email) && invitation.canResendMail(now)) {
            invitation.markSent(now);
            sendMailAfterCommit(email, inviter.getUser().getNickname(), workspace.getName(), invitation.getToken());
        }
        log.info("워크스페이스 초대: workspaceId={}, inviterId={}, inviteeUserId={}", workspaceId, inviterId,
                invitee != null ? invitee.getUserId() : null);
        return forAdmin(List.of(invitation)).get(0);
    }

    /** 대기 중인 초대 목록 (OWNER·ADMIN) */
    public List<InvitationResponse> pendingForWorkspace(Long userId, Long workspaceId) {
        WorkspaceMember member = memberHelper.getMemberOrThrow(userId, workspaceId);
        memberHelper.checkPermission(member, WorkspaceRole.ADMIN);
        return forAdmin(invitationRepository.findPendingByWorkspace(workspaceId, now()));
    }

    /** 초대 취소 (OWNER·ADMIN) */
    @Transactional
    public void cancel(Long userId, Long workspaceId, Long invitationId) {
        WorkspaceMember member = memberHelper.getMemberOrThrow(userId, workspaceId);
        memberHelper.checkPermission(member, WorkspaceRole.ADMIN);
        invitationRepository.findByIdAndWorkspaceId(invitationId, workspaceId).ifPresent(invitationRepository::delete);
    }

    /** 나에게 온 대기 중인 초대 */
    public List<InvitationResponse> mine(Long userId) {
        Users user = userRepository.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        List<WorkspaceInvitation> invitations = invitationRepository.findPendingForUser(userId, verifiedEmail(user), now());
        return forInvitee(invitations);
    }

    /** 초대 링크 미리보기 (로그인 전에도) */
    public InvitationPreview preview(String token) {
        WorkspaceInvitation invitation = pending(token);
        Workspace workspace = workspaceRepository.findById(invitation.getWorkspaceId())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVITATION_NOT_FOUND));
        String inviter = userRepository.findById(invitation.getInviterId()).map(Users::getNickname).orElse("알 수 없음");
        String email = invitation.getInviteeUserId() != null
                ? userRepository.findById(invitation.getInviteeUserId()).map(Users::getEmail).orElse(null)
                : invitation.getEmail();
        return new InvitationPreview(workspace.getName(), inviter, invitation.getRole(), maskEmail(email),
                invitation.getExpiresAt());
    }

    /** 수락 → 멤버가 된다 */
    @Transactional
    public Long accept(Long userId, String token) {
        WorkspaceInvitation invitation = pending(token);
        Users user = userRepository.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        checkInvitee(invitation, user);

        Workspace workspace = workspaceRepository.findByIdForUpdate(invitation.getWorkspaceId())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVITATION_NOT_FOUND));
        boolean alreadyMember = memberRepository
                .findByWorkspaceWorkspaceIdAndUserUserId(workspace.getWorkspaceId(), userId).isPresent();
        if (!alreadyMember) {
            // 이 초대가 이미 인원에 포함돼 있으므로 멤버 수만으로 확인한다
            if (memberRepository.countByWorkspace(workspace) >= MEMBER_LIMIT) {
                throw new BusinessException(ErrorCode.MEMBER_LIMIT_EXCEEDED);
            }
            memberRepository.save(WorkspaceMember.createMember(workspace, user, invitation.getRole()));
        }
        invitationRepository.delete(invitation);
        log.info("초대 수락: workspaceId={}, userId={}", workspace.getWorkspaceId(), userId);
        return workspace.getWorkspaceId();
    }

    /** 거절 → 초대가 사라진다 */
    @Transactional
    public void decline(Long userId, String token) {
        WorkspaceInvitation invitation = pending(token);
        Users user = userRepository.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        checkInvitee(invitation, user);
        invitationRepository.delete(invitation);
    }

    /** 하루 한 번 만료된 지 30일 넘은 초대 정리 */
    @Scheduled(cron = "0 30 4 * * *")
    @Transactional
    public void purgeExpired() {
        int removed = invitationRepository.deleteExpired(now().minusDays(30));
        if (removed > 0) log.info("만료된 초대 {}건 정리", removed);
    }

    // ===== 내부 =====

    private WorkspaceInvitation pending(String token) {
        if (!StringUtils.hasText(token)) throw new BusinessException(ErrorCode.INVITATION_NOT_FOUND);
        WorkspaceInvitation invitation = invitationRepository.findByToken(token)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVITATION_NOT_FOUND));
        if (invitation.isExpired(now())) throw new BusinessException(ErrorCode.INVITATION_NOT_FOUND);
        return invitation;
    }

    /** 초대받은 계정인지: 계정으로 초대했으면 그 계정, 이메일로 초대했으면 그 이메일을 확인한 계정 */
    private void checkInvitee(WorkspaceInvitation invitation, Users user) {
        boolean ok = invitation.getInviteeUserId() != null
                ? invitation.getInviteeUserId().equals(user.getUserId())
                : invitation.getEmail() != null && invitation.getEmail().equals(verifiedEmail(user));
        if (!ok) throw new BusinessException(ErrorCode.INVITATION_NOT_FOR_YOU);
    }

    private static String verifiedEmail(Users user) {
        return user.isEmailVerified() && StringUtils.hasText(user.getEmail()) ? user.getEmail().trim().toLowerCase() : null;
    }

    private void sendMailAfterCommit(String to, String inviterName, String workspaceName, String token) {
        String link = webBaseUrl + "/invitations/" + token;
        Runnable send = () -> emailSender.sendInvitationAsync(to, inviterName, workspaceName, link);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send.run();
                }
            });
        } else {
            send.run();
        }
    }

    private Map<Long, Users> users(Collection<Long> ids) {
        Set<Long> filtered = ids.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (filtered.isEmpty()) return Map.of();
        return userRepository.findAllById(filtered).stream().collect(Collectors.toMap(Users::getUserId, Function.identity()));
    }

    private List<InvitationResponse> forAdmin(List<WorkspaceInvitation> invitations) {
        Map<Long, Users> invitees = users(invitations.stream().map(WorkspaceInvitation::getInviteeUserId).toList());
        return invitations.stream().map(i -> {
            Users invitee = i.getInviteeUserId() == null ? null : invitees.get(i.getInviteeUserId());
            return new InvitationResponse(i.getId(), null, i.getWorkspaceId(), null, null,
                    i.getInviteeUserId(),
                    invitee != null ? invitee.getNickname() : null,
                    invitee != null ? invitee.getProfileImage() : null,
                    // 가입한 사용자는 검색 결과와 같이 이메일 일부를 가린다
                    invitee != null ? maskEmail(invitee.getEmail()) : i.getEmail(),
                    i.getRole(), i.getCreatedAt(), i.getExpiresAt());
        }).toList();
    }

    private List<InvitationResponse> forInvitee(List<WorkspaceInvitation> invitations) {
        Map<Long, Users> inviters = users(invitations.stream().map(WorkspaceInvitation::getInviterId).toList());
        Map<Long, Workspace> workspaces = workspaceRepository.findAllById(
                        invitations.stream().map(WorkspaceInvitation::getWorkspaceId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Workspace::getWorkspaceId, Function.identity()));
        return invitations.stream()
                .filter(i -> workspaces.containsKey(i.getWorkspaceId()))
                .map(i -> new InvitationResponse(i.getId(), i.getToken(), i.getWorkspaceId(),
                        workspaces.get(i.getWorkspaceId()).getName(),
                        Optional.ofNullable(inviters.get(i.getInviterId())).map(Users::getNickname).orElse("알 수 없음"),
                        null, null, null, null, i.getRole(), i.getCreatedAt(), i.getExpiresAt()))
                .toList();
    }

    static String maskEmail(String email) {
        if (email == null) return null;
        int at = email.indexOf('@');
        if (at <= 0) return "***";
        String local = email.substring(0, at);
        return local.substring(0, Math.min(2, local.length())) + "***" + email.substring(at);
    }
}
