package plugins.project_by_gen_java;

import gen_java.GeneralUnion;
import gen_java.Member;
import gen_java.MemberUnion;
import gen_java.Normal;
import gen_java.NormalException;
import gen_java.UnionList;
import gen_java.UnionOfMe;
import gen_java.VisitorApi;
import org.apache.thrift.TException;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.transport.TTransport;
import org.apache.thrift.transport.TTransportException;

import java.io.Closeable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 4399《爆枪突击》公会 VisitorApi 客户端。
 *
 * 服务端是 HTTP Thrift 接口（https://save.api.4399.com/union/VisitorApi），
 * 鉴权靠 Cookie 里的 Pauth / Puser，请求基础信息见 {@link Normal}（time / game_id / arch_index）。
 * 逻辑参考 tools/VisitorApi.py。
 */
public class VisitorApiTool implements Closeable {

    public static final String DEFAULT_URL = "https://save.api.4399.com/union/VisitorApi";
    public static final String DEFAULT_GAME_ID = "100027788";
    public static final String DEFAULT_ARCH_INDEX = "0";

    private static final Pattern PAUTH_PATTERN = Pattern.compile("\"Pauth\"\\s*:\\s*\"([^\"]*)\"");
    private static final Pattern PUSER_PATTERN = Pattern.compile("\"Puser\"\\s*:\\s*\"([^\"]*)\"");
    private static final Pattern INDEX_PATTERN = Pattern.compile("\"index\"\\s*:\\s*\"?([^\",}]*)");

    private final TTransport transport;
    private final VisitorApi.Client client;
    private final String gameId;
    private final String archIndex;
    private final String cookieHeader;

    /**
     * @param pauth     Cookie 中的 Pauth（形如 uid|...）
     * @param puser     Cookie 中的 Puser（账号名）
     * @param archIndex 角色所在区服/档位，对应 Cookie 文件的 index，缺省 "0"
     */
    public VisitorApiTool(String pauth, String puser, String archIndex) throws TTransportException {
        this(pauth, puser, archIndex, DEFAULT_URL);
    }

    public VisitorApiTool(String pauth, String puser, String archIndex, String url) throws TTransportException {
        if (pauth == null || pauth.trim().isEmpty()) {
            throw new IllegalArgumentException("缺少 Cookie 字段: Pauth");
        }
        if (puser == null || puser.trim().isEmpty()) {
            throw new IllegalArgumentException("缺少 Cookie 字段: Puser");
        }
        this.archIndex = archIndex == null || archIndex.trim().isEmpty()
                ? DEFAULT_ARCH_INDEX : archIndex.trim();
        this.gameId = DEFAULT_GAME_ID;
        this.cookieHeader = "Pauth=" + pauth.trim() + "; Puser=" + puser.trim();

        ThriftHttpTransport httpTransport = new ThriftHttpTransport(url);
        httpTransport.setCustomHeaders(defaultHeaders(cookieHeader));
        TProtocol protocol = new TBinaryProtocol(httpTransport);
        this.transport = httpTransport;
        this.client = new VisitorApi.Client(protocol);
        this.transport.open();
    }

    /**
     * 从 Cookie JSON 创建（兼容 {"uid","index","cookies":{...}} 与仅含 Cookie 的对象两种格式）。
     */
    public static VisitorApiTool fromCookieJson(String cookieJson) throws TTransportException {
        return fromCookieJson(cookieJson, DEFAULT_URL);
    }

    public static VisitorApiTool fromCookieJson(String cookieJson, String url) throws TTransportException {
        if (cookieJson == null || cookieJson.trim().isEmpty()) {
            throw new IllegalArgumentException("Cookie JSON 为空");
        }
        String pauth = firstGroup(PAUTH_PATTERN, cookieJson);
        String puser = firstGroup(PUSER_PATTERN, cookieJson);
        String index = firstGroup(INDEX_PATTERN, cookieJson);
        return new VisitorApiTool(pauth, puser, index, url);
    }

    public static VisitorApiTool fromCookieMap(Map<String, String> cookies, String archIndex)
            throws TTransportException {
        if (cookies == null) {
            throw new IllegalArgumentException("Cookie 为空");
        }
        return new VisitorApiTool(cookies.get("Pauth"), cookies.get("Puser"), archIndex);
    }

    private static Map<String, String> defaultHeaders(String cookieHeader) {
        Map<String, String> headers = new LinkedHashMap<String, String>();
        headers.put("User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
        headers.put("Content-Type", "application/x-thrift");
        headers.put("Referer", "https://web.4399.com/");
        headers.put("Cookie", cookieHeader);
        return headers;
    }

    /** 每次调用都刷新 time，与服务端校验一致。 */
    private Normal newNormal() {
        return new Normal()
                .setTime(String.valueOf(System.currentTimeMillis() / 1000L))
                .setGame_id(gameId)
                .setArch_index(archIndex);
    }

    public String getGameId() {
        return gameId;
    }

    public String getArchIndex() {
        return archIndex;
    }

    public String getCookieHeader() {
        return cookieHeader;
    }

    // ==================== API ====================

    /** 查询当前用户所在公会及成员信息。 */
    public UnionOfMe unionOfMe() throws NormalException, TException {
        return client.unionOfMe(newNormal());
    }

    /** 查询可加入的公会列表。 */
    public UnionList unionList(int pageNum, int pageSize) throws NormalException, TException {
        return client.unionList(newNormal(), pageNum, pageSize);
    }

    /** 创建公会。 */
    public void unionCreate(String title, String extra) throws NormalException, TException {
        client.unionCreate(newNormal(), title, extra == null ? "" : extra);
    }

    /** 申请加入公会。 */
    public void unionApply(int unionId, String extra) throws NormalException, TException {
        client.unionApply(newNormal(), unionId, extra == null ? "" : extra);
    }

    @Override
    public void close() {
        if (transport != null && transport.isOpen()) {
            transport.close();
        }
    }

    public boolean isOpen() {
        return transport != null && transport.isOpen();
    }

    // ==================== 便捷 JSON（供 Servlet 直接返回） ====================

    public String unionOfMeJson() {
        try {
            return toJson(unionOfMe());
        } catch (Exception e) {
            return errorJson(e);
        }
    }

    public String unionListJson(int pageNum, int pageSize) {
        try {
            return toJson(unionList(pageNum, pageSize));
        } catch (Exception e) {
            return errorJson(e);
        }
    }

    public String unionCreateJson(String title, String extra) {
        try {
            unionCreate(title, extra);
            return "{\"status\":\"success\"}";
        } catch (Exception e) {
            return errorJson(e);
        }
    }

    public String unionApplyJson(int unionId, String extra) {
        try {
            unionApply(unionId, extra);
            return "{\"status\":\"success\"}";
        } catch (Exception e) {
            return errorJson(e);
        }
    }

    public static String toJson(UnionOfMe result) {
        if (result == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder("{");
        append(sb, "time", result.getTime()).append(',');
        sb.append("\"compose\":").append(toJson(result.getCompose()));
        return sb.append('}').toString();
    }

    public static String toJson(gen_java.Compose compose) {
        if (compose == null) {
            return "null";
        }
        return "{\"member_union\":" + toJson(compose.getMember_union())
                + ",\"member\":" + toJson(compose.getMember()) + "}";
    }

    public static String toJson(UnionList result) {
        if (result == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder("{");
        append(sb, "time", result.getTime()).append(',');
        append(sb, "count", result.getCount()).append(',');
        sb.append("\"general_unions\":[");
        List<GeneralUnion> unions = result.getGeneral_unions();
        if (unions != null) {
            for (int i = 0; i < unions.size(); i++) {
                if (i > 0) {
                    sb.append(',');
                }
                sb.append(toJson(unions.get(i)));
            }
        }
        return sb.append("]}").toString();
    }

    public static String toJson(GeneralUnion union) {
        if (union == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"id\":").append(union.getId()).append(',');
        append(sb, "name", union.getName()).append(',');
        append(sb, "creator", union.getCreator()).append(',');
        sb.append("\"level\":").append(union.getLevel()).append(',');
        append(sb, "members_num", union.getMembers_num()).append(',');
        append(sb, "extra", union.getExtra()).append(',');
        sb.append("\"contribution\":").append(union.getContribution());
        return sb.append('}').toString();
    }

    public static String toJson(MemberUnion union) {
        if (union == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"id\":").append(union.getId()).append(',');
        sb.append("\"game_id\":").append(union.getGame_id()).append(',');
        append(sb, "index", union.getIndex()).append(',');
        append(sb, "uid", union.getUid()).append(',');
        append(sb, "username", union.getUsername()).append(',');
        append(sb, "nickname", union.getNickname()).append(',');
        append(sb, "title", union.getTitle()).append(',');
        sb.append("\"level\":").append(union.getLevel()).append(',');
        append(sb, "members_num", union.getMembers_num()).append(',');
        sb.append("\"experience\":").append(union.getExperience()).append(',');
        sb.append("\"contribution\":").append(union.getContribution()).append(',');
        append(sb, "transfer", union.getTransfer()).append(',');
        append(sb, "dissolve_date", union.getDissolve_date()).append(',');
        append(sb, "extra", union.getExtra());
        return sb.append('}').toString();
    }

    public static String toJson(Member member) {
        if (member == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"union_id\":").append(member.getUnion_id()).append(',');
        sb.append("\"game_id\":").append(member.getGame_id()).append(',');
        append(sb, "index", member.getIndex()).append(',');
        append(sb, "uid", member.getUid()).append(',');
        append(sb, "username", member.getUsername()).append(',');
        append(sb, "nickname", member.getNickname()).append(',');
        append(sb, "role_id", member.getRole_id()).append(',');
        append(sb, "role_name", member.getRole_name()).append(',');
        append(sb, "active_time", member.getActive_time()).append(',');
        sb.append("\"contribution\":").append(member.getContribution()).append(',');
        append(sb, "extra", member.getExtra());
        return sb.append('}').toString();
    }

    private static String errorJson(Exception e) {
        String message;
        if (e instanceof NormalException) {
            NormalException ne = (NormalException) e;
            message = "接口返回错误 id=" + ne.getId() + ", error=" + ne.getError();
        } else {
            message = e.getClass().getSimpleName() + ": " + e.getMessage();
        }
        return "{\"status\":\"error\",\"message\":\"" + escapeJson(message) + "\"}";
    }

    private static StringBuilder append(StringBuilder sb, String name, String value) {
        sb.append('"').append(name).append("\":");
        if (value == null) {
            sb.append("null");
        } else {
            sb.append('"').append(escapeJson(value)).append('"');
        }
        return sb;
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

    private static String firstGroup(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1).trim() : "";
    }
}