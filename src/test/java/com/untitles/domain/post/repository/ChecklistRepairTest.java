package com.untitles.domain.post.repository;

import com.untitles.domain.post.entity.Post;
import com.untitles.domain.user.entity.Users;
import com.untitles.domain.user.repository.UserRepository;
import com.untitles.domain.workspace.entity.Workspace;
import com.untitles.domain.workspace.repository.WorkspaceRepository;
import com.untitles.global.util.HtmlSanitizer;
import com.untitles.support.H2JpaTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

@H2JpaTest
class ChecklistRepairTest {

    @Autowired PostRepository postRepository;
    @Autowired UserRepository userRepository;
    @Autowired WorkspaceRepository workspaceRepository;

    private static final String EDITOR_HTML =
            "<ul data-type=\"taskList\"><li data-checked=\"true\" data-type=\"taskItem\"><label><input type=\"checkbox\" checked><span></span></label><div><p>a</p></div></li></ul>";

    @Test
    @DisplayName("저장해도 체크리스트 항목 표시(data-type)가 남는다")
    void sanitizerKeepsTaskItem() {
        String clean = new HtmlSanitizer().sanitize(EDITOR_HTML);
        assertThat(clean).contains("data-type=\"taskItem\"").contains("data-checked=\"true\"");
    }

    @Test
    @DisplayName("예전에 표시가 빠진 채 저장된 체크리스트를 한 번만 복구한다")
    void repairsBrokenChecklists() {
        Users user = userRepository.save(Users.createLocal(null, "fixme", "pw", "고치기"));
        Workspace ws = workspaceRepository.save(Workspace.createPersonal());
        Post broken = postRepository.save(Post.create("t",
                "<ul data-type=\"taskList\"><li data-checked=\"true\"><p>a</p></li><li data-checked=\"false\"><p>b</p></li></ul>",
                user, ws, null));

        assertThat(postRepository.repairChecklistItems()).isEqualTo(1);
        assertThat(postRepository.repairChecklistItems()).isZero();

        String content = postRepository.findById(broken.getPostId()).orElseThrow().getContent();
        assertThat(content).isEqualTo(
                "<ul data-type=\"taskList\"><li data-type=\"taskItem\" data-checked=\"true\"><p>a</p></li><li data-type=\"taskItem\" data-checked=\"false\"><p>b</p></li></ul>");
    }
}
