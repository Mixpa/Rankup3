package sh.okx.rankup.messages;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import sh.okx.rankup.prestige.Prestige;
import sh.okx.rankup.prestige.Prestiges;
import sh.okx.rankup.ranks.Rank;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MessageBuilder {
  private String message;

  public MessageBuilder(String message) {
    this.message = message;
  }

  public static MessageBuilder of(ConfigurationSection config, Message message) {
    return MessageBuilder.of(config, message.getName());
  }

  private static MessageBuilder of(ConfigurationSection config, String message) {
    String string = config.getString(message);
    Objects.requireNonNull(string, "Configuration message '" + message + "' not found!");
    return new MessageBuilder(ChatColor.translateAlternateColorCodes('&', string));
  }

  public MessageBuilder replace(Variable variable, Object value) {
    return replace(variable.name(), value);
  }

  public MessageBuilder replace(String name, Object value) {
    Pattern pattern = Pattern.compile("\\{" + name + "}", Pattern.CASE_INSENSITIVE);
    Matcher matcher = pattern.matcher(message);
    this.message = matcher.replaceAll(String.valueOf(value));
    return this;
  }

  public MessageBuilder replaceFirstPrestige(Rank rank, Prestiges prestiges, String with) {
    if (prestiges != null && prestiges.getFirst().equals(rank)) {
      replace(Variable.OLD_RANK, with);
    }
    return this;
  }

  public MessageBuilder replaceRanks(CommandSender player, String rankName) {
    replace(Variable.PLAYER, player.getName());
    replaceRanks(rankName);
    return this;
  }

  public MessageBuilder replaceRanks(CommandSender player, Rank oldRank, String rankName) {
    replace(Variable.PLAYER, player.getName());
    replaceRanks(oldRank, rankName);
    return this;
  }

  public MessageBuilder replaceRanks(String rankName) {
    replace(Variable.RANK, rankName);
    return this;
  }

  public MessageBuilder replaceRanks(Rank oldRank, String rankName) {
    replaceRanks(rankName);
    replace(Variable.OLD_RANK, oldRank.getRank());
    return this;
  }

  public MessageBuilder replaceFromTo(Rank rank) {
    if (rank instanceof Prestige) {
      Prestige prestige = (Prestige) rank;
      replace(Variable.FROM, prestige.getFrom());
      replace(Variable.TO, prestige.getTo());
    }
    return this;
  }

  /**
   * Fails the MessageBuilder if the message is empty.
   * if this fails, all subsequent calls to that MessageBuilder will do nothing
   *
   * @return an EmptyMessageBuilder if the message is empty, itself otherwise
   */
  public MessageBuilder failIfEmpty() {
    return failIf(message.isEmpty());
  }

  public MessageBuilder failIf(boolean value) {
    if (value) {
      return new EmptyMessageBuilder();
    } else {
      return this;
    }
  }

  public void send(CommandSender sender) {
    String msg = message;
    if (sender instanceof Player && Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
      msg = PlaceholderAPI.setPlaceholders((Player) sender, msg);
    }
    sender.sendMessage(msg);
  }

  /**
   * Sends the message to all players
   * ie, calls MessageBuilder#send(Player) for all players online, and sends the message in the console.
   */
  public void broadcast() {
    for (Player player : Bukkit.getOnlinePlayers()) {
      send(player);
    }
    send(Bukkit.getConsoleSender());
  }

  @Override
  public String toString() {
    return message;
  }
}
