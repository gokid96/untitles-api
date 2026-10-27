package com.untitles.domain.auth.service;

import com.untitles.domain.onboarding.event.UserSignedUpEvent;
import com.untitles.domain.user.entity.Users;
import com.untitles.domain.user.repository.UserRepository;
import com.untitles.domain.workspace.entity.Workspace;
import com.untitles.domain.workspace.entity.WorkspaceMember;
import com.untitles.domain.workspace.repository.WorkspaceMemberRepository;
import com.untitles.domain.workspace.repository.WorkspaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 새 계정 공통 준비: 개인 워크스페이스 생성, 가입 이벤트(예시 노트) 발행, 겹치지 않는 닉네임 만들기
 */
@Service
@RequiredArgsConstructor
public class AccountSetupService {

    private static final int NICKNAME_MAX = 40;

    private final UserRepository userRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final ApplicationEventPublisher eventPublisher;

    /** 저장된 새 사용자에게 개인 워크스페이스를 만들고 가입 이벤트를 발행한다 (호출한 트랜잭션 안에서) */
    public void setUp(Users savedUser) {
        Workspace personal = workspaceRepository.save(Workspace.createPersonal());
        workspaceMemberRepository.save(WorkspaceMember.createOwner(personal, savedUser));
        eventPublisher.publishEvent(new UserSignedUpEvent(savedUser.getUserId(), personal.getWorkspaceId()));
    }

    /** 이미 쓰는 닉네임이면 뒤에 숫자를 붙인다 */
    public String uniqueNickname(String base) {
        String nickname = StringUtils.hasText(base) ? base.trim() : "사용자";
        if (nickname.length() < 2) nickname = nickname + "님";
        if (nickname.length() > NICKNAME_MAX) nickname = nickname.substring(0, NICKNAME_MAX);

        String candidate = nickname;
        int count = 1;
        while (userRepository.existsByNickname(candidate)) {
            candidate = nickname + count++;
        }
        return candidate;
    }
}
