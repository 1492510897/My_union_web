package com.server;

import gen_java.UserApi;
import org.apache.thrift.TException;
import java.sql.*;

public class UserServerImpl implements UserApi.Iface {

    private static final String DB_PASS = "yzh20070911";
    private static final String USER_DB_URL = "jdbc:mysql://localhost:3306/user_db?useSSL=false&serverTimezone=UTC&characterEncoding=utf8";

    @Override
    public String registerLocal(String username, String password) throws TException {
        // 保持不变...
        System.out.println("收到注册请求: " + username);
        try (Connection conn = DriverManager.getConnection(USER_DB_URL, "root", DB_PASS)) {
            PreparedStatement check = conn.prepareStatement("SELECT id FROM users WHERE username = ?");
            check.setString(1, username);
            if (check.executeQuery().next()) return "注册失败：用户名已存在";

            PreparedStatement insert = conn.prepareStatement("INSERT INTO users (name, username, password) VALUES (?, ?, ?)", Statement.RETURN_GENERATED_KEYS);
            insert.setString(1, username); 
            insert.setString(2, username); 
            insert.setString(3, password); 
            insert.executeUpdate();

            ResultSet rs = insert.getGeneratedKeys();
            if (rs.next()) {
                int newUserId = rs.getInt(1);
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
            
            // 👇 1. 先查 users 表，把 default_uids 和 default_cookie 也查出来
            String userSql = "SELECT id, name, default_uids, default_cookie FROM users WHERE username = ? AND password = ?";
            PreparedStatement userStmt = conn.prepareStatement(userSql);
            userStmt.setString(1, username);
            userStmt.setString(2, password);
            ResultSet userRs = userStmt.executeQuery();

            if (userRs.next()) {
                int id = userRs.getInt("id");
                String name = userRs.getString("name");
                String defaultUids = userRs.getString("default_uids");
                String defaultCookie = userRs.getString("default_cookie");
                
                if (defaultUids == null) defaultUids = "";
                if (defaultCookie == null) defaultCookie = "";

                // 2. 查 cookies 表，获取绑定的所有小号
                StringBuilder accountsJson = new StringBuilder("[");
                String cookieSql = "SELECT uids, cookie FROM cookies WHERE user_id = ?";
                PreparedStatement cookieStmt = conn.prepareStatement(cookieSql);
                cookieStmt.setInt(1, id);
                ResultSet cookieRs = cookieStmt.executeQuery();

                boolean hasAccount = false;
                while (cookieRs.next()) {
                    hasAccount = true;
                    String uids = cookieRs.getString("uids");
                    String cookie = cookieRs.getString("cookie");
                    if (cookie == null) cookie = "";
                    
                    accountsJson.append(String.format("{\"uids\":\"%s\", \"cookie\":\"%s\"},", uids, cookie));
                }

                if (hasAccount) {
                    accountsJson.setLength(accountsJson.length() - 1); // 去掉末尾逗号
                }
                accountsJson.append("]");

                // 3. 拼装最终的 JSON（新增了 default_uids 和 default_cookie）
                String jsonResult = String.format(
                    "{\"status\":\"success\", \"id\":%d, \"name\":\"%s\", \"default_uids\":\"%s\", \"default_cookie\":\"%s\", \"accounts\":%s}", 
                    id, name, defaultUids, defaultCookie, accountsJson.toString()
                );
                return jsonResult;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "{\"status\":\"error\", \"message\":\"服务器异常: " + e.getMessage() + "\"}";
        }
        
        return "{\"status\":\"fail\", \"message\":\"账号或密码错误\"}";
    }
}