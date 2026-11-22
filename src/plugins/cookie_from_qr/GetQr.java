package plugins.cookie_from_qr;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 4399 扫码登录：获取二维码图片、轮询扫码状态、提取登录 Cookie。
 * 逻辑参考 tools/cookie_from_qr.py。
 *
 * 一个实例对应一次扫码流程（二维码 + 会话 Cookie），由上层（Servlet 的 HttpSession）持有。
 */
public class GetQr implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final String BASE_URL = "https://ptlogin.4399.com";

    private static final String APP_ID = "www_home";
    private static final String HOME_URL = "https://www.4399.com/";
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    /** 二维码有效期，与参考脚本一致：2 分钟。 */
    public static final long TIMEOUT_MILLIS = 2 * 60 * 1000L;

    /** 两次向 4399 轮询的最小间隔，避免前端高频刷新被限流。 */
    private static final long MIN_POLL_INTERVAL_MILLIS = 3000L;

    /** 需要从 4399 保存下来的 Cookie 字段。 */
    public static final String[] TARGET_KEYS = {
            "USESSIONID", "ptusertype", "Uauth", "Pauth",
            "ck_accname", "Puser", "Xauth", "Pnick", "Qnick"
    };

    private static final Pattern CODE_PATTERN = Pattern.compile("\"code\"\\s*:\\s*(-?\\d+)");
    private static final Pattern RESULT_PATTERN = Pattern.compile("\"result\"\\s*:\\s*(-?\\d+)");

    public enum Status {
        /** 等待扫码 / 等待手机确认 */
        WAITING,
        /** 扫码成功，Cookie 已提取 */
        SUCCESS,
        /** 二维码已过期 */
        EXPIRED
    }

    private final Map<String, String> cookies = new LinkedHashMap<String, String>();
    private final long createdAt = System.currentTimeMillis();

    private byte[] qrImage;
    private Status status = Status.WAITING;
    private String uid = "";
    private Map<String, String> loginCookies;
    private long lastPollAt;

    /**
     * 获取二维码图片（同一实例只请求一次）。
     */
    public synchronized byte[] fetchQrImage() throws IOException {
        if (qrImage == null) {
            String url = BASE_URL + "/ptlogin/qr.do?appId=" + APP_ID + "&gameId=";
            HttpResult result = get(url);
            if (result.statusCode != HttpURLConnection.HTTP_OK || result.body.length == 0) {
                throw new IOException("获取二维码失败，HTTP " + result.statusCode);
            }
            qrImage = result.body;
        }
        return qrImage;
    }

    /**
     * 轮询一次扫码状态；扫码成功时自动访问一次首页以补全 Cookie 并提取登录凭证。
     */
    public synchronized Status poll() throws IOException {
        if (status == Status.SUCCESS) {
            return status;
        }
        if (isExpired()) {
            status = Status.EXPIRED;
            return status;
        }
        long now = System.currentTimeMillis();
        if (now - lastPollAt < MIN_POLL_INTERVAL_MILLIS) {
            return status;
        }
        lastPollAt = now;

        // 没有二维码就说明会话还没初始化，顺手初始化，保证轮询用的是同一个会话
        fetchQrImage();

        String pollUrl = BASE_URL + "/ptlogin/qr_status.do?appId=" + APP_ID
                + "&gameId=&v=0&_=" + System.currentTimeMillis();
        HttpResult result = get(pollUrl);
        String text = new String(result.body, StandardCharsets.UTF_8);

        Integer code = intField(text, CODE_PATTERN);
        Integer scanResult = intField(text, RESULT_PATTERN);

        if (code != null && code == 300 && scanResult != null && scanResult == 1) {
            completeLogin();
        }
        return status;
    }

    /**
     * 扫码确认后：访问首页补全 Cookie，按指定字段提取，并从 Pauth 解析 uid。
     */
    private void completeLogin() throws IOException {
        try {
            get(HOME_URL);
        } catch (IOException ignored) {
            // 首页请求失败不影响已下发的 Cookie
        }

        Map<String, String> snapshot = new LinkedHashMap<String, String>();
        for (String key : TARGET_KEYS) {
            String value = cookies.get(key);
            snapshot.put(key, value == null ? "" : value);
        }

        loginCookies = Collections.unmodifiableMap(snapshot);
        uid = extractUid(snapshot);
        status = Status.SUCCESS;
    }

    private static String extractUid(Map<String, String> cookieMap) {
        // 优先 Pauth（"uid|..."），其次使用账号/昵称作为兜底
        String[] candidates = {cookieMap.get("Pauth"), cookieMap.get("Puser"), cookieMap.get("ck_accname")};
        for (String candidate : candidates) {
            if (candidate == null || candidate.trim().isEmpty()) {
                continue;
            }
            int index = candidate.indexOf('|');
            String value = index >= 0 ? candidate.substring(0, index) : candidate;
            value = value.trim();
            if (!value.isEmpty()) {
                return value;
            }
        }
        return "";
    }

    public synchronized Status getStatus() {
        return status;
    }

    public synchronized String getUid() {
        return uid;
    }

    public synchronized Map<String, String> getLoginCookies() {
        return loginCookies;
    }

    public synchronized boolean isExpired() {
        return status != Status.SUCCESS && System.currentTimeMillis() - createdAt > TIMEOUT_MILLIS;
    }

    /**
     * 把提取到的 Cookie 序列化为 JSON，供写入数据库 / 前端使用。
     */
    public synchronized String getCookieJson() {
        if (loginCookies == null) {
            return "{}";
        }
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (String key : TARGET_KEYS) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append('"').append(escapeJson(key)).append("\":\"")
                    .append(escapeJson(loginCookies.get(key))).append('"');
        }
        return sb.append('}').toString();
    }

    public static String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }

    // ==================== HTTP 工具 ====================

    private HttpResult get(String url) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        try {
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(15000);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("User-Agent", USER_AGENT);
            conn.setRequestProperty("Referer", "https://www.4399.com/");
            conn.setRequestProperty("Accept", "image/webp,image/apng,image/*,*/*;q=0.8");
            conn.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
            String cookieHeader = cookieHeader();
            if (!cookieHeader.isEmpty()) {
                conn.setRequestProperty("Cookie", cookieHeader);
            }

            int statusCode = conn.getResponseCode();
            InputStream in = statusCode >= 400 ? conn.getErrorStream() : conn.getInputStream();
            byte[] body = readAll(in);
            mergeCookies(conn);
            return new HttpResult(statusCode, body);
        } finally {
            conn.disconnect();
        }
    }

    private String cookieHeader() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : cookies.entrySet()) {
            if (sb.length() > 0) {
                sb.append("; ");
            }
            sb.append(entry.getKey()).append('=').append(entry.getValue());
        }
        return sb.toString();
    }

    private void mergeCookies(HttpURLConnection conn) {
        Map<String, List<String>> headers = conn.getHeaderFields();
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            if (entry.getKey() == null || !"Set-Cookie".equalsIgnoreCase(entry.getKey())) {
                continue;
            }
            List<String> values = entry.getValue();
            if (values == null) {
                continue;
            }
            for (String raw : values) {
                if (raw == null) {
                    continue;
                }
                String nameValue = raw.split(";", 2)[0].trim();
                int eq = nameValue.indexOf('=');
                if (eq <= 0) {
                    continue;
                }
                String name = nameValue.substring(0, eq).trim();
                String value = nameValue.substring(eq + 1).trim();
                if (!name.isEmpty()) {
                    cookies.put(name, value);
                }
            }
        }
    }

    private static byte[] readAll(InputStream in) throws IOException {
        if (in == null) {
            return new byte[0];
        }
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int len;
            while ((len = in.read(buffer)) != -1) {
                out.write(buffer, 0, len);
            }
            return out.toByteArray();
        } finally {
            in.close();
        }
    }

    private static Integer intField(String text, Pattern pattern) {
        Matcher matcher = pattern.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        try {
            return Integer.valueOf(matcher.group(1));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static final class HttpResult {
        private final int statusCode;
        private final byte[] body;

        private HttpResult(int statusCode, byte[] body) {
            this.statusCode = statusCode;
            this.body = body;
        }
    }
}