package fr.sukikui.mineverify.command;

import fr.sukikui.mineverify.config.RemoteAppConfig;
import fr.sukikui.mineverify.link.LinkRequest;
import fr.sukikui.mineverify.link.LinkRequestStore;
import fr.sukikui.mineverify.message.MineVerifyMessages;
import fr.sukikui.mineverify.message.MineVerifyMessenger;
import fr.sukikui.mineverify.remote.RemoteAppPoller;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Handles the player-facing MineVerify command.
 */
public final class MineVerifyCommand implements CommandExecutor {

  private static final String ADMIN_PERMISSION = "mineverify.admin";

  private final MineVerifyMessenger messenger;
  private final MineVerifyStatusRenderer statusRenderer;
  private final LinkRequestStore requestStore;
  private final RemoteAppPoller poller;
  private final Executor asyncExecutor;

  /**
   * Creates a MineVerify command handler.
   */
  public MineVerifyCommand(
      MineVerifyMessages messages,
      LinkRequestStore requestStore,
      RemoteAppPoller poller,
      Executor asyncExecutor) {
    messenger = new MineVerifyMessenger(Objects.requireNonNull(messages, "messages"));
    this.requestStore = Objects.requireNonNull(requestStore, "requestStore");
    this.poller = Objects.requireNonNull(poller, "poller");
    statusRenderer = new MineVerifyStatusRenderer(poller, requestStore);
    this.asyncExecutor = Objects.requireNonNull(asyncExecutor, "asyncExecutor");
  }

  @Override
  public boolean onCommand(
      @NotNull CommandSender sender,
      @NotNull Command command,
      @NotNull String label,
      @NotNull String[] args) {
    if (args.length > 0 && args[0].equalsIgnoreCase("status")) {
      return handleStatus(sender, args);
    }

    if (!(sender instanceof Player player)) {
      messenger.sendPlayerOnly(sender);
      return true;
    }

    if (args.length == 0) {
      poller.trigger();
      messenger.sendPollingStarted(sender);
      return true;
    }

    if (args.length != 1) {
      messenger.sendUsage(sender);
      return true;
    }

    Optional<LinkRequest> request =
        requestStore.validateCode(args[0], player.getUniqueId(), player.getName(), Instant.now());
    if (request.isEmpty()) {
      messenger.sendInvalidCode(sender);
      return true;
    }

    messenger.sendAccepted(sender, appName(request.get()));
    asyncExecutor.execute(() -> poller.reportValidation(request.get()));
    return true;
  }

  private boolean handleStatus(CommandSender sender, String[] args) {
    if (!sender.hasPermission(ADMIN_PERMISSION)) {
      statusRenderer.sendNoPermission(sender);
      return true;
    }

    if (args.length == 1) {
      statusRenderer.sendStatus(sender, false);
      return true;
    }

    if (args.length == 2 && args[1].equalsIgnoreCase("requests")) {
      statusRenderer.sendStatus(sender, true);
      return true;
    }

    statusRenderer.sendUsage(sender);
    return true;
  }

  private String appName(LinkRequest request) {
    RemoteAppConfig app = poller.apps().get(request.appId());
    if (app == null) {
      return request.appId();
    }
    return app.name();
  }
}
