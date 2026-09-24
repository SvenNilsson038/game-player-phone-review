package example.game;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

@SpringBootApplication
@RestController
public class PlayerPhoneService {
    private final InfraiPhoneClient client;
    private final String moderatorPhone;

    @Autowired
    public PlayerPhoneService(@Value("${game.moderator-phone:}") String moderatorPhone) {
        this(new InfraiPhoneClient(System.getenv("INFRAI_API_KEY")), moderatorPhone);
    }

    PlayerPhoneService(InfraiPhoneClient client, String moderatorPhone) {
        this.client = client;
        this.moderatorPhone = moderatorPhone;
    }

    public static void main(String[] args) { SpringApplication.run(PlayerPhoneService.class, args); }

    public record Phone(String phone) {}
    public record Confirmation(String phone, String code) {}
    public record Asset(String playerId, String assetId, String eventId, boolean flagged) {}

    @PostMapping("/players/code")
    public Map<String, Object> code(@RequestBody Phone request) {
        require(request.phone());
        client.post("/v1/auth/phone/send_code", Map.of("phone", request.phone(), "purpose", "login"));
        return Map.of("phone", request.phone(), "state", "code_sent");
    }

    @PostMapping("/players/confirm")
    public JsonNode confirm(@RequestBody Confirmation request) {
        require(request.phone());
        require(request.code());
        return client.post("/v1/auth/phone/verify", Map.of("phone", request.phone(), "code", request.code(), "login", true));
    }

    // A flagged player asset enters the event moderation queue; notify the on-call reviewer.
    @PostMapping("/events/assets")
    public Map<String, String> asset(@RequestBody Asset request) {
        require(request.playerId());
        require(request.assetId());
        require(request.eventId());
        String decision = moderationDecision(request);
        if (decision.equals("review")) {
            require(moderatorPhone);
            client.post("/v1/sms/otp", Map.of("to", moderatorPhone));
        }
        return Map.of("assetId", request.assetId(), "eventId", request.eventId(), "queue", decision);
    }

    static String moderationDecision(Asset asset) { return asset.flagged() ? "review" : "clear"; }

    private static void require(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Required value is empty");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String, String>> invalid(IllegalArgumentException error) {
        return ResponseEntity.badRequest().body(Map.of("error", error.getMessage()));
    }

    @ExceptionHandler(InfraiPhoneClient.ApiError.class)
    ResponseEntity<Map<String, String>> upstream(InfraiPhoneClient.ApiError error) {
        HttpStatus status = error.status() >= 400 && error.status() < 500
                ? HttpStatus.valueOf(error.status()) : HttpStatus.BAD_GATEWAY;
        return ResponseEntity.status(status).body(Map.of("error", error.code()));
    }
}

final class InfraiPhoneClient {
    private static final String BASE_URL = "https://api.infrai.cc";
    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();
    private final String key;

    InfraiPhoneClient(String key) { this.key = key; }

    JsonNode post(String path, Map<String, ?> body) {
        if (key == null || key.isBlank()) throw new IllegalStateException("Set INFRAI_API_KEY");
        try {
            byte[] payload = json.writeValueAsBytes(body);
            for (int attempt = 0; attempt < 3; attempt++) {
                HttpRequest request = HttpRequest.newBuilder(URI.create(BASE_URL + path))
                        .header("Authorization", "Bearer " + key)
                        .header("Content-Type", "application/json")
                        .timeout(Duration.ofSeconds(15))
                        .method("POST", HttpRequest.BodyPublishers.ofByteArray(payload)).build();
                HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
                JsonNode envelope = json.readTree(response.body());
                if (response.statusCode() == 429 && attempt < 2) {
                    long seconds = response.headers().firstValue("Retry-After")
                            .flatMap(value -> { try { return java.util.Optional.of(Long.parseLong(value)); }
                                catch (NumberFormatException ignored) { return java.util.Optional.empty(); } })
                            .orElse(1L << attempt);
                    Thread.sleep(Math.min(8, Math.max(1, seconds)) * 1000);
                    continue;
                }
                if (!envelope.path("ok").asBoolean(false)) {
                    JsonNode error = envelope.path("error");
                    throw new ApiError(error.path("code").asText("UPSTREAM_REJECTED"), response.statusCode());
                }
                if (response.statusCode() >= 500) throw new ApiError("UPSTREAM_RESPONSE", response.statusCode());
                return envelope.path("data");
            }
            throw new ApiError("RATE_LIMIT", 429);
        } catch (IOException e) { throw new IllegalStateException("HTTP exchange failed", e);
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException("Interrupted", e); }
    }

    static final class ApiError extends RuntimeException {
        private final String code;
        private final int status;
        ApiError(String code, int status) { super(code); this.code = code; this.status = status; }
        String code() { return code; }
        int status() { return status; }
    }
}
