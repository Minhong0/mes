package com.minimes.integration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * db/ddl, db/seed의 SQL 파일을 JDBC로 실행한다.
 * 이 파일들은 sqlcmd용이라 'GO' 줄로 배치를 나눈다. GO는 SQL 문법이 아니라 sqlcmd의 구분자라서
 * JDBC로 실행하려면 직접 GO 기준으로 잘라 한 덩어리씩 보내야 한다.
 */
final class SqlScriptRunner {

    // 한 줄에 GO만 있는 줄 (앞뒤 공백 허용, 대소문자 무시)
    private static final Pattern GO_LINE = Pattern.compile("(?im)^\\s*GO\\s*$");

    private SqlScriptRunner() {
    }

    /** 폴더 안의 .sql 파일을 파일 이름 순서(00_, 01_ ...)로 실행한다. */
    static void runDirectory(Connection conn, Path dir) throws IOException, SQLException {
        try (Stream<Path> files = Files.list(dir)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".sql")).sorted().toList()) {
                runFile(conn, file);
            }
        }
    }

    static void runFile(Connection conn, Path file) throws IOException, SQLException {
        String sql = Files.readString(file, StandardCharsets.UTF_8).replace("\r\n", "\n");
        try (Statement st = conn.createStatement()) {
            for (String batch : GO_LINE.split(sql)) {
                if (!batch.isBlank()) {
                    try {
                        st.execute(batch);
                    } catch (SQLException e) {
                        throw new SQLException(file.getFileName() + " 실행 실패: " + e.getMessage(), e);
                    }
                }
            }
        }
    }
}
