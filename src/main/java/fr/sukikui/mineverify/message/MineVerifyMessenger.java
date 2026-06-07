package fr.sukikui.mineverify.message;

import org.bukkit.command.CommandSender;

/**
 * Sends MineVerify messages using the BiomeMap chat style.
 */
public final class MineVerifyMessenger {

  private static final String APP_COLOR = "§b";
  private static final String ERROR_COLOR = "§c";
  private static final String INFO_COLOR = "§7";
  private static final String SUCCESS_COLOR = "§a";

  private final MineVerifyMessages messages;

  /**
   * Creates a messenger for one loaded language.
   */
  public MineVerifyMessenger(MineVerifyMessages messages) {
    this.messages = messages;
  }

  /**
   * Sends command usage.
   */
  public void sendUsage(CommandSender sender) {
    sendInfo(sender, messages.usage());
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
    sendInfo(sender, messages.pollingStarted());
  }

  /**
   * Sends generated code delivery feedback.
   */
  public void sendCodeCreated(CommandSender sender, String appName) {
    sendInfo(sender, messages.codeCreated(appName(appName, INFO_COLOR)));
  }

  /**
   * Sends successful local validation feedback.
   */
  public void sendAccepted(CommandSender sender, String appName) {
    sendSuccess(sender, messages.accepted(appName(appName, SUCCESS_COLOR)));
  }

  private void sendInfo(CommandSender sender, String message) {
    sender.sendMessage(MineVerifyChatStyle.PREFIX + INFO_COLOR + message);
  }

  private void sendSuccess(CommandSender sender, String message) {
    sender.sendMessage(MineVerifyChatStyle.PREFIX + SUCCESS_COLOR + message);
  }

  private void sendError(CommandSender sender, String message) {
    sender.sendMessage(MineVerifyChatStyle.PREFIX + ERROR_COLOR + "Error: " + message);
  }

  private String appName(String appName, String nextColor) {
    return APP_COLOR + appName + nextColor;
  }
}
