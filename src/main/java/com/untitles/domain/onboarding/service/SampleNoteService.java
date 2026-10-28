package com.untitles.domain.onboarding.service;

import com.untitles.domain.folder.entity.Folder;
import com.untitles.domain.folder.repository.FolderRepository;
import com.untitles.domain.link.service.PostLinkService;
import com.untitles.domain.post.entity.Post;
import com.untitles.domain.post.repository.PostRepository;
import com.untitles.domain.user.entity.Users;
import com.untitles.domain.user.repository.UserRepository;
import com.untitles.domain.workspace.entity.Workspace;
import com.untitles.domain.workspace.repository.WorkspaceRepository;
import com.untitles.global.util.HtmlSanitizer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 새 사용자의 개인 워크스페이스에 "시작하기" 폴더와 사용법 노트 3개를 만든다.
 * 노트끼리 링크로 이어 두어 그래프와 백링크도 바로 보이게 한다.
 */
@Service
@RequiredArgsConstructor
public class SampleNoteService {

    static final String FOLDER_NAME = "시작하기";
    static final String WELCOME_TITLE = "Untitles에 오신 걸 환영해요";
    static final String LINK_TITLE = "노트 연결하기";
    static final String EDITOR_TITLE = "편집 기능 둘러보기";

    private final UserRepository userRepository;
    private final WorkspaceRepository workspaceRepository;
    private final FolderRepository folderRepository;
    private final PostRepository postRepository;
    private final PostLinkService postLinkService;
    private final HtmlSanitizer htmlSanitizer;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void createSampleNotes(Long userId, Long workspaceId) {
        Users user = userRepository.findById(userId).orElse(null);
        Workspace workspace = workspaceRepository.findById(workspaceId).orElse(null);
        if (user == null || workspace == null || postRepository.countByWorkspace(workspace) > 0) {
            return;
        }

        Folder folder = folderRepository.save(Folder.create(user, workspace, null, FOLDER_NAME));

        // 링크에 ID가 필요하므로 먼저 만들고 본문을 채운다
        Post welcome = postRepository.save(Post.create(WELCOME_TITLE, "", user, workspace, folder));
        Post linking = postRepository.save(Post.create(LINK_TITLE, "", user, workspace, folder));
        Post editor = postRepository.save(Post.create(EDITOR_TITLE, "", user, workspace, folder));

        welcome.updateContent(htmlSanitizer.sanitize(welcomeHtml(linking, editor)));
        linking.updateContent(htmlSanitizer.sanitize(linkingHtml(welcome, editor)));
        editor.updateContent(htmlSanitizer.sanitize(editorHtml(welcome)));

        for (Post post : List.of(welcome, linking, editor)) {
            postLinkService.syncLinks(post);
        }
    }

    private static String link(Post post) {
        return "<a class=\"note-link\" data-note-id=\"" + post.getPostId() + "\">" + post.getTitle() + "</a>";
    }

    private static String welcomeHtml(Post linking, Post editor) {
        return """
                <p>Untitles는 노트를 서로 <strong>연결</strong>해서 생각을 정리하는 노트 앱이에요.</p>
                <h2>먼저 해 볼 것</h2>
                <ul data-type="taskList">
                <li data-type="taskItem" data-checked="true"><p>예시 노트 열어 보기</p></li>
                <li data-type="taskItem" data-checked="false"><p>사이드바 위쪽의 <strong>새 노트</strong> 버튼(종이에 + 모양)으로 노트 만들기</p></li>
                <li data-type="taskItem" data-checked="false"><p>본문에서 <code>[[</code> 를 입력해 다른 노트 연결하기</p></li>
                <li data-type="taskItem" data-checked="false"><p>새 워크스페이스를 만들어 함께 쓸 사람 초대하기 (같은 노트를 동시에 편집할 수 있어요)</p></li>
                </ul>
                <h2>더 알아보기</h2>
                <ul>
                <li><p>%s : 링크와 그래프 사용법</p></li>
                <li><p>%s : 체크리스트, 표, 코드 블록 등</p></li>
                </ul>
                <blockquote><p>노트는 입력하는 대로 자동 저장돼요. 다 읽은 예시 노트는 지워도 괜찮아요. 휴지통에서 30일 동안 되살릴 수 있어요.</p></blockquote>
                """.formatted(link(linking), link(editor));
    }

    private static String linkingHtml(Post welcome, Post editor) {
        return """
                <p>노트끼리 이어 두면 나중에 관련된 생각을 한눈에 찾을 수 있어요.</p>
                <h2>링크 넣기</h2>
                <ol>
                <li><p>본문에서 <code>[[</code> 를 입력해요.</p></li>
                <li><p>노트 제목 일부를 입력하면 후보가 나와요.</p></li>
                <li><p>골라서 Enter를 누르면 링크가 들어가요.</p></li>
                </ol>
                <p>예를 들어 이 노트는 %s 와 %s 에 연결돼 있어요. 링크를 누르면 그 노트로 이동해요.</p>
                <h2>그래프와 백링크</h2>
                <p>사이드바 위쪽의 <strong>그래프 열기</strong> 버튼(점이 이어진 모양)을 누르면 오른쪽에 연결 패널이 열려요.</p>
                <ul>
                <li><p><strong>그래프</strong>: 지금 노트와 이어진 노트들을 점과 선으로 보여줘요.</p></li>
                <li><p><strong>백링크</strong>: 이 노트를 링크한 다른 노트 목록이에요.</p></li>
                </ul>
                """.formatted(link(welcome), link(editor));
    }

    private static String editorHtml(Post welcome) {
        return """
                <p>툴바나 단축 입력으로 다양한 형식을 쓸 수 있어요.</p>
                <h2>체크리스트</h2>
                <ul data-type="taskList">
                <li data-type="taskItem" data-checked="true"><p>장보기</p></li>
                <li data-type="taskItem" data-checked="false"><p>운동하기</p></li>
                </ul>
                <h2>표</h2>
                <table><tbody>
                <tr><th><p>할 일</p></th><th><p>마감</p></th></tr>
                <tr><td><p>기획서 초안</p></td><td><p>금요일</p></td></tr>
                </tbody></table>
                <h2>코드</h2>
                <pre><code>console.log('hello, untitles')</code></pre>
                <h2>그 밖에</h2>
                <ul>
                <li><p><mark>형광펜</mark>, <strong>굵게</strong>, <em>기울임</em>, <u>밑줄</u></p></li>
                <li><p>이미지는 툴바의 사진 버튼으로 넣어요.</p></li>
                <li><p>툴바의 내려받기 버튼으로 노트를 마크다운 파일로 저장할 수 있어요.</p></li>
                <li><p>워크스페이스 설정에서 노트를 웹에 공개할 수 있어요.</p></li>
                </ul>
                <p>처음으로 돌아가기: %s</p>
                """.formatted(link(welcome));
    }
}
