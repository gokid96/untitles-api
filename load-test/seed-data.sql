-- ============================================================
--  데이터 시딩 SQL (실제 운영 패턴)
--  사용자 2,000명 × 워크스페이스 1개 × 폴더 5개 × 노트 50개
--  = posts 테이블 100,000건
--
--  BCrypt 해시: 'Test1234!' → 미리 계산한 해시값 사용
--  실행: mysql -h <RDS엔드포인트> -u admin -p untitles < seed-data.sql
-- ============================================================

SET @bcrypt_hash = '$2a$10$HpDPs7PnEd7vsvRrOzB/b.I.kDbPh3Pi0b/cE4V1gHywS/Q/q6S3q';
-- ↑ 'Test1234!'의 BCrypt 해시 (strength 10)

SET @now = NOW();

-- 외래키 체크 잠시 끄기 (성능)
SET FOREIGN_KEY_CHECKS = 0;
SET UNIQUE_CHECKS = 0;
SET autocommit = 0;

-- ============================================================
-- 1. 사용자 2,000명 생성
-- ============================================================
DROP PROCEDURE IF EXISTS seed_users;
DELIMITER //
CREATE PROCEDURE seed_users()
BEGIN
    DECLARE i INT DEFAULT 1;
    WHILE i <= 2000 DO
        INSERT INTO users (email, login_id, password, nickname, created_at, updated_at)
        VALUES (
            CONCAT('seeduser', LPAD(i, 4, '0'), '@test.com'),
            CONCAT('seeduser', LPAD(i, 4, '0')),
            @bcrypt_hash,
            CONCAT('테스트유저', i),
            @now,
            @now
        );
        SET i = i + 1;
    END WHILE;
END //
DELIMITER ;

CALL seed_users();
COMMIT;

-- 시딩 사용자 ID 범위 확인
SET @min_user_id = (SELECT MIN(user_id) FROM users WHERE login_id LIKE 'seeduser%');
SET @max_user_id = (SELECT MAX(user_id) FROM users WHERE login_id LIKE 'seeduser%');
SELECT CONCAT('사용자 생성: ', @min_user_id, ' ~ ', @max_user_id) AS progress;

-- ============================================================
-- 2. 워크스페이스 2,000개 생성 (사용자당 1개)
-- ============================================================
DROP PROCEDURE IF EXISTS seed_workspaces;
DELIMITER //
CREATE PROCEDURE seed_workspaces()
BEGIN
    DECLARE i INT DEFAULT 0;
    DECLARE uid INT;
    SET uid = @min_user_id;
    WHILE uid <= @max_user_id DO
        SET i = i + 1;
        -- 워크스페이스 생성
        INSERT INTO workspace (name, description, type, publish_all, created_at, updated_at)
        VALUES (
            CONCAT('ws-seeduser', LPAD(i, 4, '0')),
            CONCAT('테스트유저', i, '의 워크스페이스'),
            'PERSONAL',
            FALSE,
            @now,
            @now
        );

        -- 워크스페이스 멤버 등록 (OWNER)
        INSERT INTO workspace_member (workspace_id, user_id, role, joined_at)
        VALUES (LAST_INSERT_ID(), uid, 'OWNER', @now);

        SET uid = uid + 1;
    END WHILE;
END //
DELIMITER ;

CALL seed_workspaces();
COMMIT;

SET @min_ws_id = (SELECT MIN(workspace_id) FROM workspace WHERE name LIKE 'ws-seeduser%');
SET @max_ws_id = (SELECT MAX(workspace_id) FROM workspace WHERE name LIKE 'ws-seeduser%');
SELECT CONCAT('워크스페이스 생성: ', @min_ws_id, ' ~ ', @max_ws_id) AS progress;

-- ============================================================
-- 3. 폴더 10,000개 생성 (워크스페이스당 5개)
-- ============================================================
DROP PROCEDURE IF EXISTS seed_folders;
DELIMITER //
CREATE PROCEDURE seed_folders()
BEGIN
    DECLARE ws_id INT;
    DECLARE uid INT;
    DECLARE f INT;

    SET ws_id = @min_ws_id;
    SET uid = @min_user_id;

    WHILE ws_id <= @max_ws_id DO
        SET f = 1;
        WHILE f <= 5 DO
            INSERT INTO folder (name, workspace_id, user_id, parent_id, publish_all, created_at, updated_at)
            VALUES (
                CONCAT('folder-', f),
                ws_id,
                uid,
                NULL,
                FALSE,
                @now,
                @now
            );
            SET f = f + 1;
        END WHILE;

        SET ws_id = ws_id + 1;
        SET uid = uid + 1;
    END WHILE;
END //
DELIMITER ;

CALL seed_folders();
COMMIT;

SELECT CONCAT('폴더 생성: ', COUNT(*), '개') AS progress FROM folder WHERE name LIKE 'folder-%';

-- ============================================================
-- 4. 노트 100,000건 생성 (워크스페이스당 50개)
-- ============================================================
DROP PROCEDURE IF EXISTS seed_posts;
DELIMITER //
CREATE PROCEDURE seed_posts()
BEGIN
    DECLARE ws_id INT;
    DECLARE uid INT;
    DECLARE p INT;
    DECLARE folder_id_val INT;
    DECLARE ws_count INT DEFAULT 0;

    SET ws_id = @min_ws_id;
    SET uid = @min_user_id;

    WHILE ws_id <= @max_ws_id DO
        -- 이 워크스페이스의 첫 번째 폴더 ID 가져오기
        SET folder_id_val = (
            SELECT MIN(folder_id) FROM folder WHERE workspace_id = ws_id
        );

        SET p = 1;
        WHILE p <= 50 DO
            -- 5개 폴더에 돌아가며 분배, 10개는 루트(폴더 없음)
            INSERT INTO post (title, content, version, is_public, is_excluded, user_id, workspace_id, folder_id, created_at, updated_at)
            VALUES (
                CONCAT('note-', ws_id, '-', p),
                CONCAT('<p>워크스페이스 ', ws_id, '의 노트 ', p, '번 내용입니다. ',
                       REPEAT('이것은 테스트 데이터입니다. 실제 노트와 비슷한 분량을 위해 적당한 길이의 텍스트를 포함합니다. ', 5 + (p % 20)),
                       '</p>'),
                0,
                FALSE,
                FALSE,
                uid,
                ws_id,
                IF(p <= 40,
                   folder_id_val + ((p - 1) % 5),  -- 40개는 폴더에 분배
                   NULL                              -- 10개는 루트
                ),
                @now,
                @now
            );
            SET p = p + 1;
        END WHILE;

        SET ws_count = ws_count + 1;
        IF ws_count % 100 = 0 THEN
            COMMIT;
            SELECT CONCAT('노트 진행: ', ws_count, '/', @max_ws_id - @min_ws_id + 1, ' 워크스페이스') AS progress;
        END IF;

        SET ws_id = ws_id + 1;
        SET uid = uid + 1;
    END WHILE;
END //
DELIMITER ;

CALL seed_posts();
COMMIT;

-- ============================================================
-- 정리
-- ============================================================
SET FOREIGN_KEY_CHECKS = 1;
SET UNIQUE_CHECKS = 1;
SET autocommit = 1;

-- 프로시저 정리
DROP PROCEDURE IF EXISTS seed_users;
DROP PROCEDURE IF EXISTS seed_workspaces;
DROP PROCEDURE IF EXISTS seed_folders;
DROP PROCEDURE IF EXISTS seed_posts;

-- 결과 확인
SELECT '=== 시딩 결과 ===' AS info;
SELECT CONCAT('사용자: ', COUNT(*), '명') AS result FROM users WHERE login_id LIKE 'seeduser%';
SELECT CONCAT('워크스페이스: ', COUNT(*), '개') AS result FROM workspace WHERE name LIKE 'ws-seeduser%';
SELECT CONCAT('폴더: ', COUNT(*), '개') AS result FROM folder WHERE name LIKE 'folder-%';
SELECT CONCAT('노트: ', COUNT(*), '건') AS result FROM post WHERE title LIKE 'note-%';
SELECT CONCAT('posts 테이블 전체: ', COUNT(*), '건') AS result FROM post;
