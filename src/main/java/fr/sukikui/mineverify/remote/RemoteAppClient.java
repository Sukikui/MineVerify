package fr.sukikui.mineverify.remote;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import fr.sukikui.mineverify.config.RemoteAppConfig;
import fr.sukikui.mineverify.link.LinkRequest;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * HTTP client for MineVerify outbound calls to remote apps.
 */
public final class RemoteAppClient {

  static final String PENDING_REQUESTS_PATH = "/api/mineverify/pending-requests";
  static final String CODE_CREATED_PATH = "/api/mineverify/code-created";
  static final String VALIDATED_PATH = "/api/mineverify/validated";
  static final String EXPIRED_PATH = "/api/mineverify/expired";
  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

  private final HttpClient httpClient;
  private final Gson gson = new Gson();

  /**
   * Creates a remote app client.
   */
  public RemoteAppClient(HttpClient httpClient) {
    this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
  }

  /**
   * Fetches remote requests waiting for a MineVerify code.
   */
  public PendingRemoteRequests fetchPendingRequests(RemoteAppConfig app)
      throws RemoteAppException {
    String operation = "fetch pending requests";
    String url = app.endpoint(PENDING_REQUESTS_PATH);
    HttpRequest request =
        baseRequest(url, app, operation).GET().build();
    HttpResponse<String> response = send(request, operation, url);
    ensureSuccess(response, app, operation, url);
    return new PendingRemoteRequests(
        parsePendingRequests(app, response.body(), response.statusCode(), operation, url),
        response.statusCode());
  }

  /**
   * Sends a generated MineVerify code to its remote app.
   */
  public int sendCodeCreated(RemoteAppConfig app, LinkRequest request)
      throws RemoteAppException {
    JsonObject payload = new JsonObject();
    payload.addProperty("appId", request.appId());
    payload.addProperty("requestId", request.requestId());
    payload.addProperty("code", request.code());
    payload.addProperty("expiresAt", DateTimeFormatter.ISO_INSTANT.format(request.expiresAt()));

    return postJson(app, CODE_CREATED_PATH, payload, "send created code");
  }

  /**
   * Sends a validated Minecraft identity to its remote app.
   */
  public int sendValidated(RemoteAppConfig app, LinkRequest request)
      throws RemoteAppException {
    JsonObject payload = new JsonObject();
    payload.addProperty("appId", request.appId());
    payload.addProperty("requestId", request.requestId());
    payload.addProperty("code", request.code());
    payload.addProperty("minecraftUuid", required(request.minecraftUuid(), "minecraftUuid"));
    payload.addProperty("minecraftName", required(request.minecraftName(), "minecraftName"));
    payload.addProperty("validatedAt", DateTimeFormatter.ISO_INSTANT.format(
        request.validatedAt().orElseThrow()));

    return postJson(app, VALIDATED_PATH, payload, "send validation");
  }

  /**
   * Sends an expired MineVerify request to its remote app.
   */
  public int sendExpired(RemoteAppConfig app, LinkRequest request)
      throws RemoteAppException {
    JsonObject payload = new JsonObject();
    payload.addProperty("appId", request.appId());
    payload.addProperty("requestId", request.requestId());
    payload.addProperty("code", request.code());
    payload.addProperty("expiresAt", DateTimeFormatter.ISO_INSTANT.format(request.expiresAt()));
    payload.addProperty("expiredAt", DateTimeFormatter.ISO_INSTANT.format(
        request.expiredAt().orElse(request.expiresAt())));

    return postJson(app, EXPIRED_PATH, payload, "send expiration");
  }

  private int postJson(
      RemoteAppConfig app, String path, JsonObject payload, String operation)
      throws RemoteAppException {
    String url = app.endpoint(path);
    HttpRequest request =
        baseRequest(url, app, operation)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(payload)))
            .build();
    HttpResponse<String> response = send(request, operation, url);
    ensureSuccess(response, app, operation, url);
    return response.statusCode();
  }

  private HttpRequest.Builder baseRequest(String url, RemoteAppConfig app, String operation)
      throws RemoteAppException {
    try {
      return HttpRequest.newBuilder(URI.create(url))
          .version(HttpClient.Version.HTTP_1_1)
          .timeout(REQUEST_TIMEOUT)
          .header("Authorization", "Bearer " + app.token());
    } catch (IllegalArgumentException exception) {
      throw new RemoteAppException("Invalid remote app URL", exception, operation, url);
    }
  }

  private HttpResponse<String> send(HttpRequest request, String operation, String url)
      throws RemoteAppException {
    try {
      return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    } catch (IOException exception) {
      throw new RemoteAppException("Remote app request failed", exception, operation, url);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new RemoteAppException("Remote app request interrupted", exception, operation, url);
    }
  }

  private static void ensureSuccess(
      HttpResponse<String> response, RemoteAppConfig app, String operation, String url)
      throws RemoteAppException {
    if (response.statusCode() < 200 || response.statusCode() >= 300) {
      throw new RemoteAppException(
          "Unable to " + operation + " for app " + app.id() + ": HTTP " + response.statusCode(),
          operation,
          url,
          response.statusCode());
    }
  }

  private static List<PendingRemoteRequest> parsePendingRequests(
      RemoteAppConfig app, String body, int statusCode, String operation, String url)
      throws RemoteAppException {
    try {
      JsonObject root = JsonParser.parseString(body).getAsJsonObject();
      JsonArray requests = root.getAsJsonArray("requests");
      if (requests == null) {
        return List.of();
      }

      List<PendingRemoteRequest> pending = new ArrayList<>();
      for (JsonElement element : requests) {
        JsonObject object = element.getAsJsonObject();
        String requestId = stringValue(object, "requestId");
        if (!requestId.isBlank()) {
          pending.add(new PendingRemoteRequest(app.id(), requestId));
        }
      }
      return pending;
    } catch (IllegalStateException | JsonParseException exception) {
      throw new RemoteAppException("Invalid pending request response for app " + app.id(),
          exception, operation, url, statusCode);
    }
  }

  private static String stringValue(JsonObject object, String key) {
    JsonElement value = object.get(key);
    if (value == null || value.isJsonNull()) {
      return "";
    }
    return value.getAsString();
  }

  private static String required(java.util.Optional<?> value, String field) {
    return value.map(Object::toString).orElseThrow(() -> new IllegalStateException(
        "Missing " + field + " for validated request"));
  }
}
