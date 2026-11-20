package com.server;

import gen_java.UserApi;
import org.apache.thrift.TException;
import java.sql.*;

public class UserServerImpl implements UserApi.Iface {

    // TODO: 换成你真实的 MySQL 密码
    private static final String DB_PASS = "yzh20070911";
    private static final String USER_DB_URL = "jdbc:mysql://localhost:3306/user_db?useSSL=false&serverTimezone=UTC&characterEncoding=utf8";
    private static final String GAME_DB_URL = "jdbc:mysql://localhost:3306/game_db?useSSL=false&serverTimezone=UTC&characterEncoding=utf8";

    @Override
    public String registerLocal(String username, String password) throws TException {
        System.out.println("收到注册请求: " + username);
        try (Connection conn = DriverManager.getConnection(USER_DB_URL, "root", DB_PASS)) {
            PreparedStatement check = conn.prepareStatement("SELECT id FROM users WHERE username = ?");
            check.setString(1, username);
            if (check.executeQuery().next()) return "注册失败：用户名已存在";

            PreparedStatement insert = conn.prepareStatement("INSERT INTO users (username, password) VALUES (?, ?)", Statement.RETURN_GENERATED_KEYS);
            insert.setString(1, username);
            insert.setString(2, password);
            insert.executeUpdate();

            ResultSet rs = insert.getGeneratedKeys();
            if (rs.next()) {
                int newUserId = rs.getInt(1);
                try (Connection gameConn = DriverManager.getConnection(GAME_DB_URL, "root", DB_PASS)) {
                    PreparedStatement gameStmt = gameConn.prepareStatement("INSERT INTO player_data (user_id, nickname) VALUES (?, ?)");
                    gameStmt.setInt(1, newUserId);
                    gameStmt.setString(2, "玩家_" + username);
                    gameStmt.executeUpdate();
                }
                return "注册成功";
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "服务器异常：" + e.getMessage();
        }
        return "注册失败";
    }

    @Override
    public String loginLocal(String username, String password) throws TException {
        System.out.println("收到登录请求: " + username);
        try (Connection conn = DriverManager.getConnection(USER_DB_URL, "root", DB_PASS)) {
            PreparedStatement stmt = conn.prepareStatement("SELECT id FROM users WHERE username = ? AND password = ?");
            stmt.setString(1, username);
            stmt.setString(2, password);
            if (stmt.executeQuery().next()) return "登录成功";
        } catch (Exception e) {
            e.printStackTrace();
            return "服务器异常：" + e.getMessage();
        }
        return "账号或密码错误";
    }
}