package sh.okx.rankup;

import lombok.Getter;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.permission.Permission;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.InventoryView;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import sh.okx.rankup.commands.InfoCommand;
import sh.okx.rankup.commands.PrestigeCommand;
import sh.okx.rankup.commands.PrestigesCommand;
import sh.okx.rankup.commands.RanksCommand;
import sh.okx.rankup.commands.RankupCommand;
import sh.okx.rankup.gui.Gui;
import sh.okx.rankup.gui.GuiListener;
import sh.okx.rankup.messages.EmptyMessageBuilder;
import sh.okx.rankup.messages.Message;
import sh.okx.rankup.messages.MessageBuilder;
import sh.okx.rankup.messages.Variable;
import sh.okx.rankup.placeholders.Placeholders;
import sh.okx.rankup.prestige.Prestige;
import sh.okx.rankup.prestige.Prestiges;
import sh.okx.rankup.ranks.Rank;
import sh.okx.rankup.ranks.Rankups;
import sh.okx.rankup.requirements.Requirement;
import sh.okx.rankup.requirements.RequirementRegistry;
import sh.okx.rankup.requirements.requirement.*;
import sh.okx.rankup.requirements.requirement.XpLevelRequirement;
import sh.okx.rankup.requirements.requirement.advancedachievements.AdvancedAchievementsAchievementRequirement;
import sh.okx.rankup.requirements.requirement.advancedachievements.AdvancedAchievementsTotalRequirement;
import sh.okx.rankup.requirements.requirement.mcmmo.McMMOPowerLevelRequirement;
import sh.okx.rankup.requirements.requirement.mcmmo.McMMOSkillRequirement;
import sh.okx.rankup.requirements.requirement.votingplugin.VotingPluginVotesRequirement;

import java.io.File;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Supplier;

public class Rankup extends JavaPlugin {
  @Getter
  private Permission permissions;
  @Getter
  private Economy economy;
  /**
   * The registry for listing the requirements to /rankup.
   */
  @Getter
  private RequirementRegistry requirementRegistry;
  @Getter
  private FileConfiguration messages;
  @Getter
  private FileConfiguration config;
  @Getter
  private Rankups rankups;
  @Getter
  private Prestiges prestiges;
  @Getter
  private Placeholders placeholders;
  /**
   * Players who cannot rankup/prestige for a certain amount of time.
   */
  private Map<Player, Long> cooldowns;
  private AutoRankup autoRankup;

  @Override
  public void onEnable() {
    setupPermissions();
    setupEconomy();
    reload();

    Metrics metrics = new Metrics(this);
    metrics.addCustomChart(new Metrics.SimplePie("confirmation",
        () -> config.getString("confirmation.type")));
    metrics.addCustomChart(new Metrics.AdvancedPie("requirements", () -> {
      Map<String, Integer> map = new HashMap<>();
      addAll(map, rankups);
      if (prestiges != null) {
        addAll(map, prestiges);
      }
      return map;
    }));

    if (config.getBoolean("ranks")) {
      getCommand("ranks").setExecutor(new RanksCommand(this));
    }
    if (config.getBoolean("prestige")) {
      getCommand("prestige").setExecutor(new PrestigeCommand(this));
      if (config.getBoolean("prestiges")) {
        getCommand("prestiges").setExecutor(new PrestigesCommand(this));
      }
    }

    getCommand("rankup").setExecutor(new RankupCommand(this));
    getCommand("rankup3").setExecutor(new InfoCommand(this));
    getServer().getPluginManager().registerEvents(new GuiListener(this), this);

    placeholders = new Placeholders(this);
    placeholders.register();
  }


  @Override
  public void onDisable() {
    closeInventories();
    placeholders.unregister();
  }

  public void reload() {
    cooldowns = new WeakHashMap<>();
    closeInventories();
    loadConfigs();

    if (autoRankup != null) {
      autoRankup.cancel();
    }
    long time = config.getInt("autorankup-interval") * 60 * 20;
    if (time > 0) {
      autoRankup = new AutoRankup(this);
      autoRankup.runTaskTimer(this, time, time);
    }

    if (config.getInt("version") < 4) {
      getLogger().severe("You are using an outdated config!");
      getLogger().severe("This means that some things might not work!");
      getLogger().severe("To update, please rename ALL your config files (or the folder they are in),");
      getLogger().severe("and run /rankup3 reload to generate a new config file.");
      getLogger().severe("If that does not work, restart your server.");
      getLogger().severe("You may then copy in your config values from the old config.");
      getLogger().severe("Check the changelog on the Rankup spigot page to see the changes.");
    }
  }

  private void addAll(Map<String, Integer> map, RankList<? extends Rank> ranks) {
    for (Rank rank : ranks.ranks) {
      for (Requirement requirement : rank.getRequirements()) {
        String name = requirement.getName();
        map.put(name, map.getOrDefault(name, 0) + 1);
      }
    }
  }

  /**
   * Closes all rankup inventories on disable
   * so players cannot grab items from the inventory
   * on a plugin reload.
   */
  private void closeInventories() {
    for (Player player : Bukkit.getOnlinePlayers()) {
      InventoryView view = player.getOpenInventory();
      if (view.getType() == InventoryType.CHEST
          && view.getTopInventory().getHolder() instanceof Gui) {
        player.closeInventory();
      }
    }
  }

  private void loadConfigs() {
    saveLocales();

    config = loadConfig("config.yml");
    String locale = config.getString("locale", "en");
    File localeFile = new File(new File(getDataFolder(), "locale"), locale + ".yml");
    messages = YamlConfiguration.loadConfiguration(localeFile);

    Bukkit.getScheduler().scheduleSyncDelayedTask(this, this::refreshRanks);
  }

  public void refreshRanks() {
    try {
      registerRequirements();
      Bukkit.getPluginManager().callEvent(new RankupRegisterEvent(this));

      rankups = new Rankups(this, loadConfig("rankups.yml"));
      if (config.getBoolean("prestige")) {
        prestiges = new Prestiges(this, loadConfig("prestiges.yml"));
      } else {
        prestiges = null;
      }
    } catch (Exception e) {
      e.printStackTrace();
      Bukkit.getPluginManager().disablePlugin(this);
      getLogger().severe("Could not finish enabling Rankup");
    }
  }

  private void saveLocales() {
    saveLocale("en");
    saveLocale("pt-br");
    saveLocale("ru");
    saveLocale("zh_CN");
  }

  private void saveLocale(String locale) {
    String name = "locale/" + locale + ".yml";
    File file = new File(getDataFolder(), name);
    if (!file.exists()) {
      saveResource("locale/" + locale + ".yml", false);
    }
  }

  private FileConfiguration loadConfig(String name) {
    File file = new File(getDataFolder(), name);
    if (!file.exists()) {
      saveResource(name, false);
    }
    return YamlConfiguration.loadConfiguration(file);
  }

  private void registerRequirements() {
    requirementRegistry = new RequirementRegistry();
    if (economy != null) {
      requirementRegistry.addRequirement(new MoneyRequirement(this));
    }
    requirementRegistry.addRequirement(new XpLevelRequirement(this));
    requirementRegistry.addRequirement(new PlaytimeMinutesRequirement(this));
    requirementRegistry.addRequirement(new GroupRequirement(this));
    requirementRegistry.addRequirement(new PermissionRequirement(this));
    requirementRegistry.addRequirement(new PlaceholderRequirement(this));
    requirementRegistry.addRequirement(new WorldRequirement(this));
    requirementRegistry.addRequirement(new BlockBreakRequirement(this));
    requirementRegistry.addRequirement(new PlayerKillsRequirement(this));
    requirementRegistry.addRequirement(new MobKillsRequirement(this));
    if (Bukkit.getPluginManager().isPluginEnabled("mcMMO")) {
      requirementRegistry.addRequirement(new McMMOSkillRequirement(this));
      requirementRegistry.addRequirement(new McMMOPowerLevelRequirement(this));
    }
    if (Bukkit.getPluginManager().isPluginEnabled("AdvancedAchievements")) {
      requirementRegistry.addRequirement(new AdvancedAchievementsAchievementRequirement(this));
      requirementRegistry.addRequirement(new AdvancedAchievementsTotalRequirement(this));
    }
    if (Bukkit.getPluginManager().isPluginEnabled("VotingPlugin")) {
      requirementRegistry.addRequirement(new VotingPluginVotesRequirement(this));
    }
    requirementRegistry.addRequirement(new ItemRequirement(this));
    requirementRegistry.addRequirement(new UseItemRequirement(this));
    requirementRegistry.addRequirement(new TotalMobKillsRequirement(this));
    requirementRegistry.addRequirement(new CraftItemRequirement(this));
  }

  private void setupPermissions() {
    RegisteredServiceProvider<Permission> rsp = getServer().getServicesManager().getRegistration(Permission.class);
    permissions = rsp.getProvider();
  }

  private void setupEconomy() {
    RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
    if (rsp != null) {
      economy = rsp.getProvider();
    } else {
      getLogger().warning("No economy found. The 'money' requirement will be disabled.");
    }
  }

  public String formatMoney(double money) {
    List<String> shortened = config.getStringList("shorten");
    String suffix = "";

    for (int i = shortened.size(); i > 0; i--) {
      double value = Math.pow(10, 3 * i);
      if (money >= value) {
        money /= value;
        suffix = shortened.get(i - 1);
        break;
      }
    }

    return placeholders.getMoneyFormat().format(money) + suffix;
  }

  public MessageBuilder getMessage(Rank rank, Message message) {
    ConfigurationSection messages = rank.getSection();
    if (messages == null || !messages.isSet(message.getName())) {
      messages = this.messages;
    }
    return MessageBuilder.of(messages, message);
  }

  public MessageBuilder getMessage(Message message) {
    return MessageBuilder.of(messages, message);
  }

  private boolean checkCooldown(Player player, Rank rank) {
    if (cooldowns.containsKey(player)) {
      long time = System.currentTimeMillis() - cooldowns.get(player);
      // if time passed is less than the cooldown
      long cooldownSeconds = config.getInt("cooldown");
      long timeLeft = (cooldownSeconds * 1000) - time;
      if (timeLeft > 0) {
        long secondsLeft = (long) Math.ceil(timeLeft / 1000f);
        getMessage(rank, secondsLeft > 1 ? Message.COOLDOWN_PLURAL : Message.COOLDOWN_SINGULAR)
            .failIfEmpty()
            .replaceRanks(player, rank.getRank())
            .replaceFromTo(rank)
            .replace(Variable.SECONDS, cooldownSeconds)
            .replace(Variable.SECONDS_LEFT, secondsLeft)
            .send(player);
        return true;
      }
      // cooldown has expired so remove it
      cooldowns.remove(player);
    }
    return false;
  }

  private void applyCooldown(Player player) {
    if (config.getInt("cooldown") > 0) {
      cooldowns.put(player, System.currentTimeMillis());
    }
  }

  public void rankup(Player player) {
    if (!checkRankup(player)) {
      return;
    }

    Rank oldRank = rankups.getByPlayer(player);
    String next = oldRank.getNext();

    oldRank.applyRequirements(player);

    permissions.playerRemoveGroup(null, player, oldRank.getRank());
    permissions.playerAddGroup(null, player, next);

    getMessage(oldRank, Message.SUCCESS_PUBLIC)
        .failIfEmpty()
        .replaceRanks(player, oldRank, next)
        .broadcast();
    getMessage(oldRank, Message.SUCCESS_PRIVATE)
        .failIfEmpty()
        .replaceRanks(player, oldRank, next)
        .send(player);

    oldRank.runCommands(player, next);
    applyCooldown(player);
  }

  public boolean checkRankup(Player player) {
    return checkRankup(player, true);
  }

  /**
   * Checks if a player can rankup,
   * and if they can't, sends the player a message and returns false
   *
   * @param player the player to check if they can rankup
   * @return true if the player can rankup, false otherwise
   */
  public boolean checkRankup(Player player, boolean message) {
    Rank rank = rankups.getByPlayer(player);
    if (rankups.isLast(permissions, player)) {
      getMessage(prestiges == null ? Message.NO_RANKUP : prestiges.isLast(permissions, player) ? Message.NO_RANKUP : Message.MUST_PRESTIGE)
          .failIf(!message)
          .replaceRanks(player, rankups.getLast())
          .send(player);
      return false;
    } else if (rank == null) { // check if in ladder
      getMessage(Message.NOT_IN_LADDER)
          .failIf(!message)
          .replace(Variable.PLAYER, player.getName())
          .send(player);
      return false;
    } else if (!rank.hasRequirements(player)) { // check if they can afford it
      if (message) {
        replaceMoneyRequirements(getMessage(rank, Message.REQUIREMENTS_NOT_MET)
            .replaceRanks(player, rank, rank.getNext()), player, rank)
            .send(player);
      }
      return false;
    } else if (message && checkCooldown(player, rank)) {
      return false;
    }

    return true;
  }

  public void prestige(Player player) {
    if (!checkPrestige(player)) {
      return;
    }

    Prestige oldPrestige = prestiges.getByPlayer(player);

    oldPrestige.applyRequirements(player);

    permissions.playerRemoveGroup(null, player, oldPrestige.getFrom());
    permissions.playerAddGroup(null, player, oldPrestige.getTo());
    if (oldPrestige.getRank() != null) {
      permissions.playerRemoveGroup(null, player, oldPrestige.getRank());
    }
    permissions.playerAddGroup(null, player, oldPrestige.getNext());

    getMessage(oldPrestige, Message.PRESTIGE_SUCCESS_PUBLIC)
        .failIfEmpty()
        .replaceRanks(player, oldPrestige,oldPrestige.getNext())
        .replaceFromTo(oldPrestige)
        .broadcast();
    getMessage(oldPrestige, Message.PRESTIGE_SUCCESS_PRIVATE)
        .failIfEmpty()
        .replaceRanks(player, oldPrestige, oldPrestige.getNext())
        .replaceFromTo(oldPrestige)
        .send(player);

    oldPrestige.runCommands(player, oldPrestige.getNext());
    applyCooldown(player);
  }

  public boolean checkPrestige(Player player) {
    return checkPrestige(player, true);
  }

  public boolean checkPrestige(Player player, boolean message) {
    Prestige prestige = prestiges.getByPlayer(player);
    if (prestige == null || !prestige.isEligable(player)) { // check if in ladder
      getMessage(Message.NOT_HIGH_ENOUGH)
          .failIf(!message)
          .replace(Variable.PLAYER, player.getName())
          .send(player);
      return false;
    } else if (prestiges.isLast(permissions, player)) { // check if they are at the highest rank
      getMessage(prestige, Message.PRESTIGE_NO_PRESTIGE)
          .failIf(!message)
          .replaceRanks(player, prestige.getRank())
          .replaceFromTo(prestige)
          .send(player);
      return false;
    } else if (!prestige.hasRequirements(player)) { // check if they can afford it
      replaceMoneyRequirements(getMessage(prestige, Message.PRESTIGE_REQUIREMENTS_NOT_MET)
          .failIf(!message)
          .replaceRanks(player, prestige, prestiges.next(prestige).getRank()), player, prestige)
          .replaceFromTo(prestige)
          .send(player);
      return false;
    } else if (checkCooldown(player, prestige)) {
      return false;
    }

    return true;
  }

  public MessageBuilder replaceMoneyRequirements(MessageBuilder builder, CommandSender sender, Rank rank) {
    if (builder instanceof EmptyMessageBuilder) {
      return builder;
    }

    Requirement money = rank.getRequirement("money");
    if (money != null) {
      Double amount = null;
      if (sender instanceof Player && rank.isIn((Player) sender)) {
        if (economy != null) {
          amount = money.getRemaining((Player) sender);
        }
      } else {
        amount = money.getValueDouble();
      }
      if (amount != null && economy != null) {
        builder.replace(Variable.MONEY_NEEDED, formatMoney(amount));
        builder.replace(Variable.MONEY, formatMoney(money.getValueDouble()));
      }
    }
    if (sender instanceof Player) {
      replaceRequirements(builder, (Player) sender, rank);
    }
    return builder;
  }

  public MessageBuilder replaceRequirements(MessageBuilder builder, Player player, Rank rank) {
    DecimalFormat simpleFormat = placeholders.getSimpleFormat();
    DecimalFormat percentFormat = placeholders.getPercentFormat();
    for (Requirement requirement : rank.getRequirements()) {
      try {
        replaceRequirements(builder, Variable.AMOUNT, requirement, () -> simpleFormat.format(requirement.getValueDouble()));
        if (rank.isIn(player)) {
          replaceRequirements(builder, Variable.AMOUNT_NEEDED, requirement, () -> simpleFormat.format(requirement.getRemaining(player)));
          replaceRequirements(builder, Variable.PERCENT_LEFT, requirement,
              () -> percentFormat.format(Math.max(0, (requirement.getRemaining(player) / requirement.getValueDouble()) * 100)));
          replaceRequirements(builder, Variable.PERCENT_DONE, requirement,
              () -> percentFormat.format(Math.min(100, (1 - (requirement.getRemaining(player) / requirement.getValueDouble())) * 100)));
          replaceRequirements(builder, Variable.AMOUNT_DONE, requirement, () -> simpleFormat.format(requirement.getValueDouble() - requirement.getRemaining(player)));
        }
      } catch (NumberFormatException ignored) {
      }
    }
    return builder;
  }

  private void replaceRequirements(MessageBuilder builder, Variable variable, Requirement requirement, Supplier<Object> value) {
    builder.replace(variable + " " + requirement.getFullName(), value.get());
  }

  public MessageBuilder getMessage(CommandSender player, Message message, Rank oldRank, String rankName) {
    String oldRankName;
    if (oldRank instanceof Prestige && oldRank.getRank() == null) {
      oldRankName = ((Prestige) oldRank).getFrom();
    } else {
      oldRankName = oldRank.getRank();
    }

    return replaceMoneyRequirements(getMessage(oldRank, message)
        .replaceRanks(player, rankName)
        .replace(Variable.OLD_RANK, oldRankName), player, oldRank)
        .replaceFromTo(oldRank);
  }

  public void sendHeaderFooter(CommandSender sender, Rank rank, Message type) {
    MessageBuilder builder;
    if (rank == null) {
      builder = getMessage(type)
          .failIfEmpty()
          .replace(Variable.PLAYER, sender.getName());
    } else {
      builder = getMessage(rank, type)
          .failIfEmpty()
          .replaceRanks(sender, rank.getRank())
          .replaceFromTo(rank);
    }
    builder.send(sender);
  }

  public boolean isLegacy() {
    String version = Bukkit.getVersion();
    return !(version.contains("1.13") || version.contains("1.14"));
  }
}
