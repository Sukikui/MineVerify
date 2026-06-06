package fr.sukikui.mineverify.message;

import org.bukkit.command.CommandSender;

/**
 * Sends MineVerify messages using the BiomeMap chat style.
 */
public final class MineVerifyMessenger {

  private final MineVerifyMessages messages;

  /**
   * Creates a messenger for one loaded language.
   */
  public MineVerifyMessenger(MineVerifyMessages messages) {
    this.messages = messages;
  }

  /**
   * Sends command usage as a warning.
   */
  public void sendUsage(CommandSender sender) {
    sendWarning(sender, messages.usage());
  }

  /**
   * Sends non-player sender rejection as an error.
   */
  public void sendPlayerOnly(CommandSender sender) {
    sendError(sender, messages.playerOnly());
  }

  /**
   * Sends invalid code feedback as an error.
   */
  public void sendInvalidCode(CommandSender sender) {
    sendError(sender, messages.invalidCode());
  }

  /**
   * Sends polling trigger feedback.
   */
  public void sendPollingStarted(CommandSender sender) {
    sendWarning(sender, messages.pollingStarted());
  }

  /**
   * Sends successful local validation feedback.
   */
  public void sendAccepted(CommandSender sender, String appName) {
    sendSuccess(sender, messages.accepted(appName));
  }

  private void sendWarning(CommandSender sender, String message) {
    sender.sendMessage(MineVerifyChatStyle.PREFIX + "§6" + message);
  }

  private void sendSuccess(CommandSender sender, String message) {
    sender.sendMessage(MineVerifyChatStyle.PREFIX + "§a§l" + message);
  }

  private void sendError(CommandSender sender, String message) {
    sender.sendMessage(MineVerifyChatStyle.PREFIX + "§c§lError: §c" + message);
  }
}
