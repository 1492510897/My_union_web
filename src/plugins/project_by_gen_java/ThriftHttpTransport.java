package plugins.project_by_gen_java;

import org.apache.thrift.TConfiguration;
import org.apache.thrift.transport.TEndpointTransport;
import org.apache.thrift.transport.TTransportException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 基于 HttpURLConnection 的 TTransport 实现。
 *
 * libthrift 自带的 THttpClient 依赖 httpclient5，而本项目只引入了 libthrift，
 * 这里用 JDK 自带能力实现同样的语义：write 缓冲请求，flush 时 POST 整包，read 从响应体读取。
 */
public class ThriftHttpTransport extends TEndpointTransport {

    private final String url;
    private final Map<String, String> headers = new LinkedHashMap<String, String>();

    private final ByteArrayOutputStream requestBuffer = new ByteArrayOutputStream();
    private byte[] responseBuffer = new byte[0];
    private int responseOffset;

    private boolean open;
    private int connectTimeout = 10000;
    private int readTimeout = 20000;

    public ThriftHttpTransport(String url) throws TTransportException {
        super(new TConfiguration());
        this.url = url;
    }

    public void setConnectTimeout(int connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public void setReadTimeout(int readTimeout) {
        this.readTimeout = readTimeout;
    }

    public void setCustomHeader(String key, String value) {
        if (key == null || value == null) {
            return;
        }
        headers.put(key, value);
    }

    public void setCustomHeaders(Map<String, String> customHeaders) {
        if (customHeaders == null) {
            return;
        }
        for (Map.Entry<String, String> entry : customHeaders.entrySet()) {
            setCustomHeader(entry.getKey(), entry.getValue());
        }
    }

    @Override
    public boolean isOpen() {
        return open;
    }

    @Override
    public void open() {
        // HTTP 无状态，连接在 flush 时建立
        open = true;
    }

    @Override
    public void close() {
        open = false;
        requestBuffer.reset();
        responseBuffer = new byte[0];
        responseOffset = 0;
    }

    @Override
    public void write(byte[] buf, int off, int len) {
        requestBuffer.write(buf, off, len);
    }

    @Override
    public int read(byte[] buf, int off, int len) throws TTransportException {
        int remaining = responseBuffer.length - responseOffset;
        if (remaining <= 0) {
            throw new TTransportException(TTransportException.END_OF_FILE, "No more data to read");
        }
        int count = Math.min(remaining, len);
        System.arraycopy(responseBuffer, responseOffset, buf, off, count);
        responseOffset += count;
        countConsumedMessageBytes(count);
        return count;
    }

    @Override
    public void flush() throws TTransportException {
        byte[] payload = requestBuffer.toByteArray();
        requestBuffer.reset();

        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(connectTimeout);
            conn.setReadTimeout(readTimeout);
            conn.setRequestProperty("Content-Type", "application/x-thrift");
            conn.setRequestProperty("Accept", "application/x-thrift");
            conn.setRequestProperty("Content-Length", String.valueOf(payload.length));
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                conn.setRequestProperty(entry.getKey(), entry.getValue());
            }

            OutputStream out = conn.getOutputStream();
            try {
                out.write(payload);
                out.flush();
            } finally {
                out.close();
            }

            int status = conn.getResponseCode();
            InputStream in = status >= 400 ? conn.getErrorStream() : conn.getInputStream();
            byte[] body = readAll(in);
            if (status != HttpURLConnection.HTTP_OK) {
                throw new TTransportException("HTTP " + status + ": " + new String(body, StandardCharsets.UTF_8));
            }
            responseBuffer = body;
            responseOffset = 0;
            // 每条消息重新放宽到最大限制，否则第二条消息会被上一条的实际长度卡住
            resetConsumedMessageSize(-1);
        } catch (TTransportException e) {
            throw e;
        } catch (IOException e) {
            throw new TTransportException(e);
        } finally {
            if (conn != null) {
                conn.disconnect();
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
}
