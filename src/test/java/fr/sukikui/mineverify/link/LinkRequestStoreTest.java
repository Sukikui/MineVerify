package fr.sukikui.mineverify.link;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LinkRequestStoreTest {

  private static final Instant NOW = Instant.parse("2026-06-04T16:00:00Z");
  private static final UUID PLAYER_UUID = UUID.fromString("6f8f5771-8ec8-4b8d-bc40-8cbe2f84f5a3");

  @Test
  void storesRequestsByRemoteAppAndRequestId() {
    LinkRequestStore store = new LinkRequestStore();

    LinkRequest request = store.store("my-app", "request-1", "K7M9-P2Q4", later(), NOW);

    assertEquals(Optional.of(request), store.findByRemoteRequest("my-app", "request-1"));
    assertEquals(Optional.empty(), store.findByRemoteRequest("another-app", "request-1"));
  }

  @Test
  void validatesCodeOnlyOnce() {
    LinkRequestStore store = new LinkRequestStore();
    store.store("my-app", "request-1", "K7M9-P2Q4", later(), NOW);

    Optional<LinkRequest> validated =
        store.validateCode("k7m9-p2q4", PLAYER_UUID, "PlayerName", NOW.plusSeconds(10));

    assertTrue(validated.isPresent());
    assertEquals(LinkRequestState.VALIDATED, validated.get().state());
    assertTrue(validated.get().minecraftUuid().isPresent());
    Optional<LinkRequest> reused =
        store.validateCode("K7M9-P2Q4", PLAYER_UUID, "PlayerName", NOW.plusSeconds(11));
    assertEquals(Optional.empty(), reused);
  }

  @Test
  void exposesUnreportedCodeCreatedRequestsByApp() {
    LinkRequestStore store = new LinkRequestStore();
    LinkRequest request = store.store("my-app", "request-1", "K7M9-P2Q4", later(), NOW);

    List<LinkRequest> reports = store.pendingCodeCreatedReports("my-app");

    assertEquals(List.of(request), reports);
    request.markCodeCreatedReported();
    assertTrue(store.pendingCodeCreatedReports("my-app").isEmpty());
  }

  @Test
  void exposesUnreportedValidatedRequestsByApp() {
    LinkRequestStore store = new LinkRequestStore();
    LinkRequest request = store.store("my-app", "request-1", "K7M9-P2Q4", later(), NOW);
    request.validate(PLAYER_UUID, "PlayerName", NOW.plusSeconds(10));

    List<LinkRequest> reports = store.pendingValidationReports("my-app");

    assertEquals(List.of(request), reports);
    request.markValidationReported();
    assertTrue(store.pendingValidationReports("my-app").isEmpty());
  }

  @Test
  void expiresRequestsBeforeRemoval() {
    LinkRequestStore store = new LinkRequestStore();
    LinkRequest request = store.store("my-app", "request-1", "K7M9-P2Q4", NOW.plusSeconds(1), NOW);

    int expired = store.expirePending(NOW.plusSeconds(1));

    assertEquals(1, expired);
    assertEquals(1, store.size());
    assertEquals(List.of(request), store.pendingExpirationReports("my-app"));
    assertFalse(store.hasActiveCode("K7M9-P2Q4", NOW.plusSeconds(2)));
  }

  @Test
  void removesReportedTerminalRequests() {
    LinkRequestStore store = new LinkRequestStore();
    LinkRequest request = store.store("my-app", "request-1", "K7M9-P2Q4", NOW.plusSeconds(1), NOW);
    store.expirePending(NOW.plusSeconds(1));

    request.markExpirationReported();
    int removed = store.removeReportedTerminals();

    assertEquals(1, removed);
    assertEquals(0, store.size());
    assertEquals(Optional.empty(), store.findByRemoteRequest("my-app", "request-1"));
  }

  @Test
  void removesReportedValidatedRequests() {
    LinkRequestStore store = new LinkRequestStore();
    LinkRequest request = store.store("my-app", "request-1", "K7M9-P2Q4", later(), NOW);
    request.validate(PLAYER_UUID, "PlayerName", NOW.plusSeconds(10));

    request.markValidationReported();
    int removed = store.removeReportedTerminals();

    assertEquals(1, removed);
    assertEquals(0, store.size());
    assertEquals(Optional.empty(), store.findByRemoteRequest("my-app", "request-1"));
  }

  private static Instant later() {
    return NOW.plusSeconds(300);
  }
}
