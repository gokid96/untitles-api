package com.untitles.domain.workspace.repository;

import com.untitles.domain.user.entity.*;
import com.untitles.domain.user.repository.UserRepository;
import com.untitles.domain.workspace.entity.*;
import com.untitles.support.H2JpaTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import static org.assertj.core.api.Assertions.assertThat;

@H2JpaTest
class WorkspacePlanTest {
    @Autowired UserRepository users;
    @Autowired WorkspaceRepository workspaces;
    @Autowired WorkspaceMemberRepository members;

    @Test void onlyProOwnerUnlocksWorkspace() {
        Users free = users.save(Users.builder().nickname("free").build());
        Users pro = users.save(Users.builder().nickname("pro").plan(UserPlan.PRO).build());
        Workspace freeWorkspace = workspaces.save(Workspace.createTeam("free", null));
        Workspace proWorkspace = workspaces.save(Workspace.createPersonal());
        members.save(WorkspaceMember.createOwner(freeWorkspace, free));
        members.save(WorkspaceMember.createMember(freeWorkspace, pro, WorkspaceRole.ADMIN));
        members.save(WorkspaceMember.createOwner(proWorkspace, pro));
        assertThat(members.existsByWorkspaceWorkspaceIdAndRoleAndUserPlan(
                freeWorkspace.getWorkspaceId(), WorkspaceRole.OWNER, UserPlan.PRO)).isFalse();
        assertThat(members.existsByWorkspaceWorkspaceIdAndRoleAndUserPlan(
                proWorkspace.getWorkspaceId(), WorkspaceRole.OWNER, UserPlan.PRO)).isTrue();
        assertThat(members.existsByWorkspaceWorkspaceIdAndRoleAndUserPlan(
                -1L, WorkspaceRole.OWNER, UserPlan.PRO)).isFalse();
    }
}
