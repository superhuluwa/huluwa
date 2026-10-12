package com.huluwa.console.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huluwa.console.common.BusinessException;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.net.ConnectException;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.net.http.WebSocketHandshakeException;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.SecureRandom;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * 飞牛系统登录：WebSocket 获取 RSA 公钥与会话 ID，RSA/AES 加密 user.login 请求。
 * 只返回 fnos-token；密码和两步验证码不写日志、不返回前端。
 */
final class FnosLoginClient {

    private static final Duration TIMEOUT = Duration.ofSeconds(20);
    private static final SecureRandom RANDOM = new SecureRandom();
    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();

    String login(String nasUrl, String username, String password, String twofaCode, String did) {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw new BusinessException(400, "请填写飞牛登录账号和密码");
        }
        Inbox inbox = new Inbox();
        WebSocket socket = null;
        URI wsUri = null;
        try {
            wsUri = webSocketUri(nasUrl);
            socket = http.newWebSocketBuilder().connectTimeout(TIMEOUT)
                    .buildAsync(wsUri, inbox).get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);

            String keyReqid = reqid();
            send(socket, Map.of("reqid", keyReqid, "req", "util.crypto.getRSAPub"));
            JsonNode keyReply = await(inbox, keyReqid, false, false);
            String publicKey = keyReply.path("pub").asText("");
            String sessionId = keyReply.path("si").asText("");
            if (publicKey.isBlank() || sessionId.isBlank()) {
                throw new BusinessException(400, "飞牛没有返回登录公钥或会话 ID");
            }
            String hostReqid = reqid();
            send(socket, Map.of("reqid", hostReqid, "req", "appcgi.sysinfo.getHostName"));
            await(inbox, hostReqid, false, true);

            String loginReqid = reqid();
            Map<String, Object> login = new LinkedHashMap<>();
            login.put("reqid", loginReqid);
            login.put("user", username);
            login.put("password", password);
            login.put("stay", true);
            login.put("deviceType", "Browser");
            login.put("deviceName", "Huluwa Console");
            login.put("did", did);
            login.put("req", "user.login");
            login.put("si", sessionId);
            sendEncrypted(socket, publicKey, login);
            JsonNode reply = await(inbox, loginReqid, true, false);

            if (reply.path("token").isTextual() && "succ".equals(reply.path("result").asText())) {
                return reply.path("token").asText();
            }
            if (reply.path("isTwofaEnforced").asBoolean(false)
                    && !reply.path("isBindTwofaSecret").asBoolean(false)) {
                throw new BusinessException(400, "飞牛账号要求先绑定两步验证，请在飞牛网页完成绑定");
            }
            String accessToken = reply.path("accessToken").asText("");
            if (!accessToken.isBlank()) {
                if (twofaCode == null || !twofaCode.matches("\\d{6}")) {
                    throw new BusinessException(400, "飞牛账号需要两步验证码，请填写当前 6 位验证码后重试");
                }
                String twofaReqid = reqid();
                Map<String, Object> verify = new LinkedHashMap<>();
                verify.put("reqid", twofaReqid);
                verify.put("code", twofaCode);
                verify.put("isTrustedDevice", true);
                verify.put("accessToken", accessToken);
                verify.put("stay", 1);
                verify.put("deviceName", "Huluwa Console");
                verify.put("deviceType", "Browser");
                verify.put("did", did);
                verify.put("req", "user.2fa.loginVerify");
                verify.put("si", sessionId);
                sendEncrypted(socket, publicKey, verify);
                reply = await(inbox, twofaReqid, true, false);
                if (reply.path("token").isTextual() && "succ".equals(reply.path("result").asText())) {
                    return reply.path("token").asText();
                }
            }
            String message = reply.path("msg").asText(reply.path("errmsg").asText("账号或密码错误"));
            throw new BusinessException(400, "飞牛登录失败：" + message);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(400, explainConnectionFailure(e, wsUri));
        } finally {
            if (socket != null) {
                socket.abort();
            }
        }
    }

    static URI webSocketUri(String nasUrl) {
        URI origin = URI.create(nasUrl);
        String scheme = "https".equalsIgnoreCase(origin.getScheme()) ? "wss" : "ws";
        return URI.create(scheme + "://" + origin.getRawAuthority() + "/websocket?type=main");
    }

    private JsonNode await(Inbox inbox, String reqid, boolean authReply, boolean hostReply) throws Exception {
        long deadline = System.nanoTime() + TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            String message = inbox.messages.poll(500, TimeUnit.MILLISECONDS);
            if (message == null) {
                if (inbox.closed) {
                    throw new BusinessException(400, "飞牛登录连接中断");
                }
                continue;
            }
            JsonNode value = json.readTree(message);
            if (reqid.equals(value.path("reqid").asText(""))
                    || (hostReply && value.path("data").has("hostName"))
                    || (authReply && !value.hasNonNull("reqid") && value.has("result")
                    && (value.has("token") || value.has("accessToken")
                    || "fail".equals(value.path("result").asText())))) {
                return value;
            }
        }
        throw new BusinessException(400, "等待飞牛登录响应超时");
    }

    private void send(WebSocket socket, Map<String, ?> payload) throws Exception {
        socket.sendText(json.writeValueAsString(payload), true).get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
    }

    private void sendEncrypted(WebSocket socket, String pem, Map<String, Object> payload) throws Exception {
        byte[] aesKey = new byte[32];
        byte[] iv = new byte[16];
        RANDOM.nextBytes(aesKey);
        RANDOM.nextBytes(iv);
        Cipher rsa = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        rsa.init(Cipher.ENCRYPT_MODE, parsePublicKey(pem));
        Cipher aes = Cipher.getInstance("AES/CBC/PKCS5Padding");
        aes.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(aesKey, "AES"), new IvParameterSpec(iv));
        Map<String, String> envelope = new LinkedHashMap<>();
        envelope.put("req", "encrypted");
        envelope.put("iv", Base64.getEncoder().encodeToString(iv));
        envelope.put("rsa", Base64.getEncoder().encodeToString(rsa.doFinal(aesKey)));
        envelope.put("aes", Base64.getEncoder().encodeToString(
                aes.doFinal(json.writeValueAsString(payload).getBytes(StandardCharsets.UTF_8))));
        send(socket, envelope);
    }

    private java.security.PublicKey parsePublicKey(String pem) throws Exception {
        boolean pkcs1 = pem.contains("BEGIN RSA PUBLIC KEY");
        String base64 = pem.replaceAll("-----BEGIN (RSA )?PUBLIC KEY-----", "")
                .replaceAll("-----END (RSA )?PUBLIC KEY-----", "")
                .replaceAll("\\s+", "");
        byte[] der = Base64.getDecoder().decode(base64);
        if (pkcs1) {
            byte[] algorithm = {0x30, 0x0d, 0x06, 0x09, 0x2a, (byte) 0x86, 0x48,
                    (byte) 0x86, (byte) 0xf7, 0x0d, 0x01, 0x01, 0x01, 0x05, 0x00};
            ByteArrayOutputStream bitString = new ByteArrayOutputStream();
            bitString.write(0);
            bitString.write(der);
            ByteArrayOutputStream body = new ByteArrayOutputStream();
            body.write(algorithm);
            body.write(asn1(0x03, bitString.toByteArray()));
            der = asn1(0x30, body.toByteArray());
        }
        return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
    }

    private static byte[] asn1(int tag, byte[] body) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(tag);
        if (body.length < 128) {
            out.write(body.length);
        } else if (body.length < 256) {
            out.write(0x81);
            out.write(body.length);
        } else {
            out.write(0x82);
            out.write(body.length >>> 8);
            out.write(body.length & 0xff);
        }
        out.write(body);
        return out.toByteArray();
    }

    private static String reqid() {
        return System.currentTimeMillis() + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private static String explainConnectionFailure(Exception error, URI wsUri) {
        Throwable cause = error;
        while ((cause instanceof ExecutionException || cause instanceof CompletionException)
                && cause.getCause() != null) {
            cause = cause.getCause();
        }
        String target = wsUri == null ? "飞牛地址" : wsUri.getHost() + ":" +
                (wsUri.getPort() >= 0 ? wsUri.getPort() : "wss".equals(wsUri.getScheme()) ? 443 : 80);
        if (cause instanceof ConnectException) {
            return "连接飞牛登录服务失败：后端无法连接 " + target
                    + "。请核对飞牛 IP 和端口，并确认后端所在机器或容器能访问该地址";
        }
        if (cause instanceof UnknownHostException) {
            return "连接飞牛登录服务失败：无法解析 " + target + "，请检查飞牛地址";
        }
        if (cause instanceof WebSocketHandshakeException) {
            return "飞牛 WebSocket 握手失败（" + target + "）：请检查协议、端口和反向代理的 WebSocket 转发";
        }
        String detail = cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
        return "连接飞牛登录服务失败（" + target + "）：" + detail;
    }

    private static final class Inbox implements WebSocket.Listener {
        private final LinkedBlockingQueue<String> messages = new LinkedBlockingQueue<>();
        private final StringBuilder partial = new StringBuilder();
        private volatile boolean closed;

        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            partial.append(data);
            if (last) {
                messages.offer(partial.toString());
                partial.setLength(0);
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            closed = true;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            closed = true;
            return null;
        }
    }
}
