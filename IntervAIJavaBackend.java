import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public class IntervAIJavaBackend {
    private static final int PORT = 8000;
    private static final String SESSION_COOKIE = "intervai_session";
    private static final long SESSION_LIFETIME_MS = 7L * 24L * 60L * 60L * 1000L;
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final SecureRandom RANDOM = new SecureRandom();

    private static final Map<String, UserRecord> USERS = new ConcurrentHashMap<>();
    private static final Map<String, SessionRecord> SESSIONS = new ConcurrentHashMap<>();
    private static final Path APP_ROOT = Path.of(System.getProperty("user.dir"));
    private static final Path STATIC_ROOT = APP_ROOT.resolve("index.html");

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.createContext("/", IntervAIJavaBackend::handleRequest);
        server.setExecutor(null);
        server.start();
        System.out.println("IntervAI Java backend running at http://127.0.0.1:" + PORT);
    }

    private static void handleRequest(HttpExchange exchange) throws IOException {
        try {
            String method = exchange.getRequestMethod();
            String path = getPath(exchange.getRequestURI());

            if ("GET".equalsIgnoreCase(method)) {
                if ("/api/me".equals(path)) {
                    Map<String, Object> userPayload = new LinkedHashMap<>();
                    userPayload.put("user", currentUser(exchange));
                    sendJson(exchange, 200, userPayload);
                    return;
                }
                Path file = resolveStaticFile(path);
                if (file == null) {
                    sendText(exchange, 404, "Not found");
                    return;
                }
                byte[] content = Files.readAllBytes(file);
                String contentType = Files.probeContentType(file);
                if (contentType == null) {
                    contentType = "application/octet-stream";
                }
                Headers headers = exchange.getResponseHeaders();
                headers.set("Content-Type", contentType + "; charset=utf-8");
                headers.set("X-Content-Type-Options", "nosniff");
                exchange.sendResponseHeaders(200, content.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(content);
                }
                return;
            }

            if ("POST".equalsIgnoreCase(method)) {
                if ("/api/signup".equals(path)) {
                    signup(exchange);
                    return;
                }
                if ("/api/login".equals(path)) {
                    login(exchange);
                    return;
                }
                if ("/api/logout".equals(path)) {
                    logout(exchange);
                    return;
                }
                sendJson(exchange, 404, Map.of("error", "Not found."));
                return;
            }

            sendText(exchange, 405, "Method not allowed");
        } catch (Exception ex) {
            ex.printStackTrace();
            sendJson(exchange, 500, Map.of("error", "Internal server error."));
        }
    }

    private static void signup(HttpExchange exchange) throws IOException {
        Map<String, String> payload = readRequestBody(exchange);
        if (payload.isEmpty()) {
            sendJson(exchange, 400, Map.of("error", "Invalid request."));
            return;
        }

        String name = payload.getOrDefault("name", "").trim();
        String email = payload.getOrDefault("email", "").trim().toLowerCase(Locale.ROOT);
        String password = payload.getOrDefault("password", "");

        if (name.isEmpty() || name.length() > 80) {
            sendJson(exchange, 400, Map.of("error", "Enter a name up to 80 characters."));
            return;
        }
        if (email.length() > 254 || !EMAIL_PATTERN.matcher(email).matches()) {
            sendJson(exchange, 400, Map.of("error", "Enter a valid email address."));
            return;
        }
        if (password.length() < 8 || password.length() > 1024) {
            sendJson(exchange, 400, Map.of("error", "Password must be at least 8 characters."));
            return;
        }
        if (USERS.containsKey(email)) {
            sendJson(exchange, 409, Map.of("error", "An account with that email already exists."));
            return;
        }

        try {
            String[] hashed = hashPassword(password);
            UserRecord user = new UserRecord(name, email, hashed[0], hashed[1]);
            USERS.put(email, user);
            String token = createSession(email);
            sendJson(exchange, 201, Map.of("user", Map.of("name", name, "email", email)),
                    List.of(cookieHeader(token, SESSION_LIFETIME_MS / 1000)));
        } catch (GeneralSecurityException e) {
            sendJson(exchange, 500, Map.of("error", "Unable to create account."));
        }
    }

    private static void login(HttpExchange exchange) throws IOException {
        Map<String, String> payload = readRequestBody(exchange);
        if (payload.isEmpty()) {
            sendJson(exchange, 400, Map.of("error", "Invalid request."));
            return;
        }

        String email = payload.getOrDefault("email", "").trim().toLowerCase(Locale.ROOT);
        String password = payload.getOrDefault("password", "");
        if (email.isEmpty() || password.isEmpty() || password.length() > 1024) {
            sendJson(exchange, 400, Map.of("error", "Please check your details."));
            return;
        }

        UserRecord user = USERS.get(email);
        if (user == null) {
            sendJson(exchange, 401, Map.of("error", "Email or password is incorrect."));
            return;
        }

        try {
            if (!isPasswordValid(password, user.passwordSalt, user.passwordHash)) {
                sendJson(exchange, 401, Map.of("error", "Email or password is incorrect."));
                return;
            }
        } catch (GeneralSecurityException e) {
            sendJson(exchange, 401, Map.of("error", "Email or password is incorrect."));
            return;
        }

        String token = createSession(user.email);
        sendJson(exchange, 200, Map.of("user", Map.of("name", user.name, "email", user.email)),
                List.of(cookieHeader(token, SESSION_LIFETIME_MS / 1000)));
    }

    private static void logout(HttpExchange exchange) throws IOException {
        String token = readSessionToken(exchange);
        if (token != null) {
            SESSIONS.remove(token);
        }
        sendJson(exchange, 200, Map.of("ok", true), List.of(cookieHeader("", 0)));
    }

    private static Map<String, Object> currentUser(HttpExchange exchange) {
        String token = readSessionToken(exchange);
        if (token == null) {
            return null;
        }
        SessionRecord session = SESSIONS.get(token);
        if (session == null || session.expiresAtMs <= System.currentTimeMillis()) {
            SESSIONS.remove(token);
            return null;
        }
        UserRecord user = USERS.get(session.email);
        if (user == null) {
            return null;
        }
        Map<String, Object> userMap = new LinkedHashMap<>();
        userMap.put("name", user.name);
        userMap.put("email", user.email);
        return userMap;
    }

    private static String createSession(String email) {
        byte[] randomBytes = new byte[24];
        RANDOM.nextBytes(randomBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        SESSIONS.put(token, new SessionRecord(email, System.currentTimeMillis() + SESSION_LIFETIME_MS));
        return token;
    }

    private static String readSessionToken(HttpExchange exchange) {
        Headers headers = exchange.getRequestHeaders();
        List<String> cookies = headers.get("Cookie");
        if (cookies == null || cookies.isEmpty()) {
            return null;
        }
        for (String cookieHeader : cookies) {
            for (String part : cookieHeader.split(";")) {
                String candidate = part.trim();
                if (candidate.startsWith(SESSION_COOKIE + "=")) {
                    return candidate.substring((SESSION_COOKIE + "=").length());
                }
            }
        }
        return null;
    }

    private static String cookieHeader(String token, long maxAgeSeconds) {
        StringBuilder sb = new StringBuilder();
        sb.append(SESSION_COOKIE).append("=").append(token).append("; Path=/; HttpOnly; SameSite=Lax; Max-Age=")
          .append(maxAgeSeconds);
        if (maxAgeSeconds > 0) {
            sb.append("; expires=").append(java.time.Instant.now().plusSeconds(maxAgeSeconds).toString());
        }
        return sb.toString();
    }

    private static Path resolveStaticFile(String path) {
        String clean = path == null ? "/" : path;
        if ("/".equals(clean) || "/index.html".equals(clean)) {
            return STATIC_ROOT.resolve("index.html");
        }
        if ("/login.html".equals(clean) || "/signup.html".equals(clean)) {
            return STATIC_ROOT.resolve("auth.html");
        }
        Path candidate = STATIC_ROOT.resolve(clean.substring(1).replace('/', java.io.File.separatorChar));
        if (Files.exists(candidate) && !Files.isDirectory(candidate)) {
            return candidate;
        }
        return null;
    }

    private static String getPath(URI uri) {
        String raw = uri.getPath();
        return raw == null ? "/" : raw;
    }

    private static Map<String, String> readRequestBody(HttpExchange exchange) throws IOException {
        byte[] rawBytes = exchange.getRequestBody().readAllBytes();
        if (rawBytes.length == 0) {
            return Map.of();
        }
        String body = new String(rawBytes, StandardCharsets.UTF_8);
        return parseJsonObject(body);
    }

    private static Map<String, String> parseJsonObject(String body) {
        Map<String, String> result = new LinkedHashMap<>();
        String value = body == null ? "" : body.trim();
        if (value.isEmpty() || !value.startsWith("{") || !value.endsWith("}")) {
            return result;
        }

        int index = 1;
        while (index < value.length()) {
            while (index < value.length() && Character.isWhitespace(value.charAt(index))) {
                index++;
            }
            if (index >= value.length() || value.charAt(index) == '}') {
                break;
            }
            if (value.charAt(index) != '"') {
                return result;
            }
            int keyStart = index + 1;
            int keyEnd = keyStart;
            boolean escaped = false;
            while (keyEnd < value.length()) {
                char ch = value.charAt(keyEnd);
                if (escaped) {
                    escaped = false;
                } else if (ch == '\\') {
                    escaped = true;
                } else if (ch == '"') {
                    break;
                }
                keyEnd++;
            }
            if (keyEnd >= value.length()) {
                return result;
            }
            String key = unescapeJsonString(value.substring(keyStart, keyEnd));
            index = keyEnd + 1;
            while (index < value.length() && Character.isWhitespace(value.charAt(index))) {
                index++;
            }
            if (index >= value.length() || value.charAt(index) != ':') {
                return result;
            }
            index++;
            while (index < value.length() && Character.isWhitespace(value.charAt(index))) {
                index++;
            }
            if (index >= value.length()) {
                return result;
            }
            char next = value.charAt(index);
            String text;
            if (next == '"') {
                int textStart = index + 1;
                int textEnd = textStart;
                escaped = false;
                while (textEnd < value.length()) {
                    char ch = value.charAt(textEnd);
                    if (escaped) {
                        escaped = false;
                    } else if (ch == '\\') {
                        escaped = true;
                    } else if (ch == '"') {
                        break;
                    }
                    textEnd++;
                }
                if (textEnd >= value.length()) {
                    return result;
                }
                text = unescapeJsonString(value.substring(textStart, textEnd));
                index = textEnd + 1;
            } else {
                int textEnd = index;
                while (textEnd < value.length() && value.charAt(textEnd) != ',') {
                    if (value.charAt(textEnd) == '}') {
                        break;
                    }
                    textEnd++;
                }
                text = value.substring(index, textEnd).trim();
                index = textEnd;
            }
            result.put(key, text);
            while (index < value.length() && Character.isWhitespace(value.charAt(index))) {
                index++;
            }
            if (index < value.length() && value.charAt(index) == ',') {
                index++;
            }
        }
        return result;
    }

    private static String unescapeJsonString(String input) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < input.length(); i++) {
            char ch = input.charAt(i);
            if (ch == '\\' && i + 1 < input.length()) {
                char next = input.charAt(i + 1);
                switch (next) {
                    case '"': out.append('"'); i++; break;
                    case '\\': out.append('\\'); i++; break;
                    case '/': out.append('/'); i++; break;
                    case 'b': out.append('\b'); i++; break;
                    case 'f': out.append('\f'); i++; break;
                    case 'n': out.append('\n'); i++; break;
                    case 'r': out.append('\r'); i++; break;
                    case 't': out.append('\t'); i++; break;
                    default: out.append(next); i++; break;
                }
            } else {
                out.append(ch);
            }
        }
        return out.toString();
    }

    private static String[] hashPassword(String password) throws GeneralSecurityException {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, 600_000, 256);
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        byte[] hash = factory.generateSecret(spec).getEncoded();
        return new String[] { toHex(salt), toHex(hash) };
    }

    private static boolean isPasswordValid(String password, String saltHex, String expectedHashHex)
            throws GeneralSecurityException {
        byte[] salt = fromHex(saltHex);
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, 600_000, 256);
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        byte[] actualHash = factory.generateSecret(spec).getEncoded();
        return MessageDigest.isEqual(actualHash, fromHex(expectedHashHex));
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            sb.append(String.format("%02x", value));
        }
        return sb.toString();
    }

    private static byte[] fromHex(String hex) {
        int len = hex.length();
        byte[] out = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            out[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    | Character.digit(hex.charAt(i + 1), 16));
        }
        return out;
    }

    private static void sendText(HttpExchange exchange, int statusCode, String text) throws IOException {
        byte[] payload = text.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(statusCode, payload.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(payload);
        }
    }

    private static void sendJson(HttpExchange exchange, int statusCode, Map<String, ?> payload) throws IOException {
        sendJson(exchange, statusCode, payload, List.of());
    }

    private static void sendJson(HttpExchange exchange, int statusCode, Map<String, ?> payload, List<String> extraHeaders)
            throws IOException {
        String json = toJson(payload);
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", "application/json; charset=utf-8");
        headers.set("Cache-Control", "no-store");
        for (String header : extraHeaders) {
            headers.add("Set-Cookie", header);
        }
        exchange.sendResponseHeaders(statusCode, body.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
        }
    }

    private static String toJson(Map<String, ?> map) {
        StringBuilder builder = new StringBuilder();
        builder.append("{");
        boolean first = true;
        for (Map.Entry<String, ?> entry : map.entrySet()) {
            if (!first) {
                builder.append(",");
            }
            first = false;
            builder.append('"').append(escapeJson(entry.getKey())).append('"').append(":").append(toJsonValue(entry.getValue()));
        }
        builder.append("}");
        return builder.toString();
    }

    private static String toJsonValue(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof String) {
            return '"' + escapeJson((String) value) + '"';
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        if (value instanceof Map<?, ?> map) {
            StringBuilder builder = new StringBuilder();
            builder.append("{");
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) {
                    builder.append(",");
                }
                first = false;
                builder.append('"').append(escapeJson(String.valueOf(entry.getKey()))).append('"')
                        .append(":").append(toJsonValue(entry.getValue()));
            }
            builder.append("}");
            return builder.toString();
        }
        if (value instanceof Iterable<?> iterable) {
            StringBuilder builder = new StringBuilder();
            builder.append("[");
            boolean first = true;
            for (Object item : iterable) {
                if (!first) {
                    builder.append(",");
                }
                first = false;
                builder.append(toJsonValue(item));
            }
            builder.append("]");
            return builder.toString();
        }
        return '"' + escapeJson(String.valueOf(value)) + '"';
    }

    private static String escapeJson(String value) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '\\': out.append("\\\\"); break;
                case '"': out.append("\\\""); break;
                case '\n': out.append("\\n"); break;
                case '\r': out.append("\\r"); break;
                case '\t': out.append("\\t"); break;
                case '\b': out.append("\\b"); break;
                case '\f': out.append("\\f"); break;
                default:
                    if (ch < 0x20) {
                        out.append(String.format("\\u%04x", (int) ch));
                    } else {
                        out.append(ch);
                    }
            }
        }
        return out.toString();
    }

    private static final class UserRecord {
        private final String name;
        private final String email;
        private final String passwordSalt;
        private final String passwordHash;

        private UserRecord(String name, String email, String passwordSalt, String passwordHash) {
            this.name = name;
            this.email = email;
            this.passwordSalt = passwordSalt;
            this.passwordHash = passwordHash;
        }
    }

    private static final class SessionRecord {
        private final String email;
        private final long expiresAtMs;

        private SessionRecord(String email, long expiresAtMs) {
            this.email = email;
            this.expiresAtMs = expiresAtMs;
        }
    }
}
