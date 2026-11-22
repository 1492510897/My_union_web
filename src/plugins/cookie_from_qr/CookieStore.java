package plugins.cookie_from_qr;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

/**
 * 把扫码得到的 Cookie 写入数据库 user_db：
 * cookies(user_id, uids, cookie)；当用户还没有默认账号时，同时写入 users.default_uids / users.default_cookie。
 */
public class CookieStore {

    private static final String DEFAULT_DB_URL =
            "jdbc:mysql://localhost:3306/user_db?useSSL=false&serverTimezone=UTC&characterEncoding=utf8";
    private static final String DEFAULT_DB_USER = "root";
    private static final String DEFAULT_DB_PASSWORD = "yzh20070911";

    static {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException ignored) {
            // 驱动由容器（WEB-INF/lib）或运行时 classpath 提供
        }
    }

    private CookieStore() {
    }

    private static Connection connect() throws SQLException {
        return DriverManager.getConnection(
                env("UNION_DB_URL", DEFAULT_DB_URL),
                env("UNION_DB_USER", DEFAULT_DB_USER),
                env("UNION_DB_PASSWORD", DEFAULT_DB_PASSWORD));
    }

    private static String env(String key, String fallback) {
        String value = System.getenv(key);
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    public static QrUser findUserByUsername(String username) throws SQLException {
        if (username == null || username.trim().isEmpty()) {
            return null;
        }
        try (Connection conn = connect();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT id, name, username FROM users WHERE username = ?")) {
            ps.setString(1, username.trim());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next()
                        ? new QrUser(rs.getInt("id"), rs.getString("username"), rs.getString("name"))
                        : null;
            }
        }
    }

    public static QrUser findUserById(int userId) throws SQLException {
        try (Connection conn = connect();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT id, name, username FROM users WHERE id = ?")) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next()
                        ? new QrUser(rs.getInt("id"), rs.getString("username"), rs.getString("name"))
                        : null;
            }
        }
    }

    /**
     * 扫码即登录：按 4399 UID 匹配本站用户，不存在则自动创建（密码随机，不可用于密码登录）。
     */
    public static QrUser findOrCreateUserByUid(String uid, String displayName) throws SQLException {
        if (uid == null || uid.trim().isEmpty()) {
            throw new SQLException("未能解析出 4399 UID");
        }
        String username = ("4399_" + uid.trim());
        if (username.length() > 50) {
            username = username.substring(0, 50);
        }
        String name = displayName == null || displayName.trim().isEmpty() ? uid.trim() : displayName.trim();
        if (name.length() > 50) {
            name = name.substring(0, 50);
        }

        try (Connection conn = connect()) {
            Integer userId = findByUsername(conn, username);
            if (userId != null) {
                return new QrUser(userId, username, name);
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO users (name, username, password) VALUES (?, ?, ?)")) {
                ps.setString(1, name);
                ps.setString(2, username);
                ps.setString(3, UUID.randomUUID().toString());
                ps.executeUpdate();
            } catch (SQLException e) {
                // 并发下可能已被其它请求创建，重查一次
                Integer existing = findByUsername(conn, username);
                if (existing == null) {
                    throw e;
                }
                return new QrUser(existing, username, name);
            }
            Integer created = findByUsername(conn, username);
            if (created == null) {
                throw new SQLException("创建用户失败: " + username);
            }
            return new QrUser(created, username, name);
        }
    }

    private static Integer findByUsername(Connection conn, String username) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT id FROM users WHERE username = ?")) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt("id") : null;
            }
        }
    }

    /**
     * 保存（或更新）某个用户绑定的 4399 账号 Cookie。
     */
    public static SaveResult saveCookie(int userId, String uids, String cookieJson) throws SQLException {
        if (uids == null || uids.trim().isEmpty()) {
            throw new SQLException("未能解析出 4399 UID，无法保存");
        }
        final String finalUids = uids.trim();
        final String finalCookie = cookieJson == null || cookieJson.trim().isEmpty() ? "{}" : cookieJson;

        try (Connection conn = connect()) {
            conn.setAutoCommit(false);
            try {
                boolean exists = false;
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT uids FROM cookies WHERE user_id = ? AND uids = ?")) {
                    ps.setInt(1, userId);
                    ps.setString(2, finalUids);
                    try (ResultSet rs = ps.executeQuery()) {
                        exists = rs.next();
                    }
                }

                boolean inserted;
                if (exists) {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "UPDATE cookies SET cookie = ? WHERE user_id = ? AND uids = ?")) {
                        ps.setString(1, finalCookie);
                        ps.setInt(2, userId);
                        ps.setString(3, finalUids);
                        ps.executeUpdate();
                    }
                    inserted = false;
                } else {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT INTO cookies (user_id, uids, cookie) VALUES (?, ?, ?)")) {
                        ps.setInt(1, userId);
                        ps.setString(2, finalUids);
                        ps.setString(3, finalCookie);
                        ps.executeUpdate();
                    }
                    inserted = true;
                }

                boolean defaultUpdated = false;
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT default_uids FROM users WHERE id = ? FOR UPDATE")) {
                    ps.setInt(1, userId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            throw new SQLException("用户不存在: " + userId);
                        }
                        String defaultUids = rs.getString("default_uids");
                        if (defaultUids == null || defaultUids.trim().isEmpty()) {
                            try (PreparedStatement update = conn.prepareStatement(
                                    "UPDATE users SET default_uids = ?, default_cookie = ? WHERE id = ?")) {
                                update.setString(1, finalUids);
                                update.setString(2, finalCookie);
                                update.setInt(3, userId);
                                defaultUpdated = update.executeUpdate() > 0;
                            }
                        }
                    }
                }

                conn.commit();
                return new SaveResult(inserted, defaultUpdated);
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }
    }

    public static final class SaveResult {
        private final boolean inserted;
        private final boolean defaultUpdated;

        SaveResult(boolean inserted, boolean defaultUpdated) {
            this.inserted = inserted;
            this.defaultUpdated = defaultUpdated;
        }

        /** true 表示新建绑定，false 表示更新已有绑定的 Cookie。 */
        public boolean isInserted() {
            return inserted;
        }

        /** true 表示该账号成为了用户的默认 4399 账号。 */
        public boolean isDefaultUpdated() {
            return defaultUpdated;
        }
    }

    public static final class QrUser {
        private final int userId;
        private final String username;
        private final String name;

        public QrUser(int userId, String username, String name) {
            this.userId = userId;
            this.username = username;
            this.name = name;
        }

        public int getUserId() {
            return userId;
        }

        public String getUsername() {
            return username;
        }

        public String getName() {
            return name;
        }
    }
}
