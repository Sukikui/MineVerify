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

    LinkRequest request = store.store("pmc-map", "request-1", "K7M9-P2Q4", later(), NOW);

    assertEquals(Optional.of(request), store.findByRemoteRequest("pmc-map", "request-1"));
    assertEquals(Optional.empty(), store.findByRemoteRequest("another-app", "request-1"));
  }

  @Test
  void validatesCodeOnlyOnce() {
    LinkRequestStore store = new LinkRequestStore();
    store.store("pmc-map", "request-1", "K7M9-P2Q4", later(), NOW);

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
  void exposesUnreportedValidatedRequestsByApp() {
    LinkRequestStore store = new LinkRequestStore();
    LinkRequest request = store.store("pmc-map", "request-1", "K7M9-P2Q4", later(), NOW);
    request.validate(PLAYER_UUID, "PlayerName", NOW.plusSeconds(10));

    List<LinkRequest> reports = store.pendingValidationReports("pmc-map");

    assertEquals(List.of(request), reports);
    request.markValidationReported();
    assertTrue(store.pendingValidationReports("pmc-map").isEmpty());
  }

  @Test
  void removesExpiredRequests() {
    LinkRequestStore store = new LinkRequestStore();
    store.store("pmc-map", "request-1", "K7M9-P2Q4", NOW.plusSeconds(1), NOW);

    int removed = store.removeExpired(NOW.plusSeconds(1));

    assertEquals(1, removed);
    assertEquals(0, store.size());
    assertFalse(store.hasActiveCode("K7M9-P2Q4", NOW.plusSeconds(2)));
  }

  private static Instant later() {
    return NOW.plusSeconds(300);
  }
}
