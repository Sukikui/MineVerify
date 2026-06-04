package fr.sukikui.mineverify;

import fr.sukikui.mineverify.command.MineVerifyCommand;
import fr.sukikui.mineverify.config.MineVerifyConfig;
import fr.sukikui.mineverify.link.LinkCodeGenerator;
import fr.sukikui.mineverify.link.LinkRequestStore;
import fr.sukikui.mineverify.remote.RemoteAppClient;
import fr.sukikui.mineverify.remote.RemoteAppPoller;
import java.net.http.HttpClient;
import java.time.Instant;
import java.util.Objects;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Paper plugin entry point for MineVerify.
 */
public final class MineVerify extends JavaPlugin {

  private LinkRequestStore requestStore;
  private RemoteAppPoller poller;
  private BukkitTask cleanupTask;

  @Override
  public void onEnable() {
    saveDefaultConfig();

    MineVerifyConfig config = MineVerifyConfig.load(getConfig());
    requestStore = new LinkRequestStore();
    RemoteAppClient remoteClient = new RemoteAppClient(HttpClient.newHttpClient());
    LinkCodeGenerator codeGenerator = new LinkCodeGenerator();

    poller = new RemoteAppPoller(config, requestStore, codeGenerator, remoteClient, getLogger());
    poller.start(this);

    registerCommand(config);
    startCleanup(config);

    if (config.apps().isEmpty()) {
      getLogger().warning("No MineVerify apps configured. Polling is disabled.");
    }
  }

  @Override
  public void onDisable() {
    if (poller != null) {
      poller.stop();
    }
    if (cleanupTask != null) {
      cleanupTask.cancel();
    }
  }

  private void registerCommand(MineVerifyConfig config) {
    MineVerifyCommand commandHandler =
        new MineVerifyCommand(
            config.messages(),
            requestStore,
            poller,
            runnable -> getServer().getScheduler().runTaskAsynchronously(this, runnable));
    PluginCommand command =
        Objects.requireNonNull(getCommand("mineverify"), "Command mineverify not defined");
    command.setExecutor(commandHandler);
  }

  private void startCleanup(MineVerifyConfig config) {
    long intervalTicks = config.cleanupInterval().toSeconds() * 20L;
    cleanupTask =
        getServer()
            .getScheduler()
            .runTaskTimerAsynchronously(
                this,
                () -> {
                  requestStore.expirePending(Instant.now());
                  requestStore.removeReportedTerminals();
                },
                intervalTicks,
                intervalTicks);
  }
}
