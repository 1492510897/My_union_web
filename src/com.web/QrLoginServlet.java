package com.web;

import plugins.cookie_from_qr.CookieStore;
import plugins.cookie_from_qr.GetQr;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.sql.SQLException;

/**
 * 4399 扫码登录接口：
 * GET  /api/qr/image   获取（或刷新）二维码图片
 * GET  /api/qr/status  轮询扫码状态
 * POST /api/qr/login   把扫码得到的 Cookie 保存到数据库
 */
@WebServlet("/api/qr/*")
public class QrLoginServlet extends HttpServlet {

    private static final String QR_SESSION_KEY = "qrcodeLogin";

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String path = pathInfo(req);
        if ("/image".equals(path)) {
            handleImage(req, resp);
        } else if ("/status".equals(path)) {
            handleStatus(req, resp);
        } else {
            writeJson(resp, 404, "{\"status\":\"error\",\"message\":\"未知接口\"}");
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        if ("/login".equals(pathInfo(req))) {
            handleLogin(req, resp);
        } else {
            writeJson(resp, 404, "{\"status\":\"error\",\"message\":\"未知接口\"}");
        }
    }

    private static String pathInfo(HttpServletRequest req) {
        String path = req.getPathInfo();
        return path == null ? "" : path;
    }

    // ==================== 1. 二维码图片 ====================

    private void handleImage(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        HttpSession session = req.getSession(true);
        GetQr qr = (GetQr) session.getAttribute(QR_SESSION_KEY);

        boolean forceRefresh = "1".equals(req.getParameter("refresh"));
        if (qr == null || qr.getStatus() == GetQr.Status.EXPIRED || forceRefresh) {
            qr = new GetQr();
            session.setAttribute(QR_SESSION_KEY, qr);
        }

        try {
            byte[] image = qr.fetchQrImage();
            resp.setContentType("image/png");
            resp.setHeader("Cache-Control", "no-store, no-cache, must-revalidate");
            resp.setContentLength(image.length);
            resp.getOutputStream().write(image);
        } catch (IOException e) {
            resp.setContentType("text/plain;charset=UTF-8");
            resp.setStatus(HttpServletResponse.SC_BAD_GATEWAY);
            resp.getWriter().write("获取二维码失败：" + e.getMessage());
        }
    }

    // ==================== 2. 轮询状态 ====================

    private void handleStatus(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        HttpSession session = req.getSession(true);
        GetQr qr = (GetQr) session.getAttribute(QR_SESSION_KEY);

        if (qr == null) {
            writeJson(resp, 200, "{\"status\":\"waiting\",\"message\":\"请先获取二维码\"}");
            return;
        }

        try {
            GetQr.Status status = qr.poll();
            if (status == GetQr.Status.SUCCESS) {
                String uid = qr.getUid();
                if (uid.isEmpty()) {
                    writeJson(resp, 200, "{\"status\":\"error\",\"message\":\"扫码成功，但未能解析出 4399 UID\"}");
                    return;
                }
                writeJson(resp, 200, "{\"status\":\"success\",\"uid\":\"" + GetQr.escapeJson(uid)
                        + "\",\"uids\":\"" + GetQr.escapeJson(uid)
                        + "\",\"cookies\":" + qr.getCookieJson() + "}");
            } else if (status == GetQr.Status.EXPIRED) {
                writeJson(resp, 200, "{\"status\":\"expired\",\"message\":\"二维码已过期，请点击刷新\"}");
            } else {
                writeJson(resp, 200, "{\"status\":\"waiting\",\"message\":\"等待扫码，请在手机 4399 App 中确认\"}");
            }
        } catch (IOException e) {
            // 单次轮询失败不终止流程，前端继续重试
            writeJson(resp, 200, "{\"status\":\"waiting\",\"message\":\"网络异常，正在重试...\"}");
        }
    }

    // ==================== 3. 保存 Cookie 到数据库 ====================

    private void handleLogin(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        req.setCharacterEncoding("UTF-8");
        HttpSession session = req.getSession(true);
        GetQr qr = (GetQr) session.getAttribute(QR_SESSION_KEY);

        if (qr == null || qr.getStatus() != GetQr.Status.SUCCESS) {
            writeJson(resp, 200, "{\"status\":\"error\",\"message\":\"请先完成扫码\"}");
            return;
        }

        String uid = qr.getUid();
        if (uid.isEmpty()) {
            writeJson(resp, 200, "{\"status\":\"error\",\"message\":\"未能解析出 4399 UID\"}");
            return;
        }

        try {
            CookieStore.QrUser user = resolveUser(req, session);
            if (user == null) {
                writeJson(resp, 200, "{\"status\":\"error\",\"message\":\"未能确定本站用户\"}");
                return;
            }

            String cookieJson = qr.getCookieJson();
            CookieStore.SaveResult result = CookieStore.saveCookie(user.getUserId(), uid, cookieJson);

            session.setAttribute("currentUser", user.getUsername());
            session.setAttribute("current4399Uid", uid);
            session.removeAttribute(QR_SESSION_KEY);

            writeJson(resp, 200, "{\"status\":\"success\""
                    + ",\"uid\":\"" + GetQr.escapeJson(uid) + "\""
                    + ",\"uids\":\"" + GetQr.escapeJson(uid) + "\""
                    + ",\"userId\":" + user.getUserId()
                    + ",\"name\":\"" + GetQr.escapeJson(user.getName()) + "\""
                    + ",\"cookies\":" + cookieJson
                    + ",\"created\":" + result.isInserted()
                    + ",\"default\":" + result.isDefaultUpdated() + "}");
        } catch (SQLException e) {
            writeJson(resp, 200, "{\"status\":\"error\",\"message\":\"保存 Cookie 失败: "
                    + GetQr.escapeJson(e.getMessage()) + "\"}");
        }
    }

    /**
     * 已登录本站时沿用该用户；否则按 4399 UID 匹配（或自动创建）一个本站用户，
     * 因此扫码本身就是登录方式，不依赖密码登录。
     */
    private CookieStore.QrUser resolveUser(HttpServletRequest req, HttpSession session) throws SQLException {
        Object currentUser = session.getAttribute("currentUser");
        if (currentUser != null) {
            CookieStore.QrUser user = CookieStore.findUserByUsername(currentUser.toString());
            if (user != null) {
                return user;
            }
        }

        String userIdParam = req.getParameter("userId");
        if (userIdParam != null && !userIdParam.trim().isEmpty()) {
            try {
                CookieStore.QrUser user = CookieStore.findUserById(Integer.parseInt(userIdParam.trim()));
                if (user != null) {
                    return user;
                }
            } catch (NumberFormatException ignored) {
                // 非法 id，继续走扫码建档
            }
        }

        GetQr qr = (GetQr) session.getAttribute(QR_SESSION_KEY);
        String nickname = "";
        if (qr != null && qr.getLoginCookies() != null) {
            nickname = qr.getLoginCookies().get("Pnick");
            if (nickname == null || nickname.trim().isEmpty()) {
                nickname = qr.getLoginCookies().get("Qnick");
            }
        }
        return CookieStore.findOrCreateUserByUid(qr == null ? "" : qr.getUid(), nickname);
    }

    private static void writeJson(HttpServletResponse resp, int status, String json) throws IOException {
        resp.setStatus(status);
        resp.setContentType("application/json;charset=UTF-8");
        resp.setHeader("Cache-Control", "no-store");
        resp.getWriter().write(json);
    }
}
