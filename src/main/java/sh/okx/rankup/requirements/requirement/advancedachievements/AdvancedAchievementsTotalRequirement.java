package sh.okx.rankup.requirements.requirement.advancedachievements;

import com.hm.achievement.api.AdvancedAchievementsAPIFetcher;
import org.bukkit.entity.Player;
import sh.okx.rankup.Rankup;
import sh.okx.rankup.requirements.Requirement;
import sh.okx.rankup.requirements.ProgressiveRequirement;

public class AdvancedAchievementsTotalRequirement extends ProgressiveRequirement {
  public AdvancedAchievementsTotalRequirement(Rankup plugin) {
    super(plugin, "advancedachievements-total");
  }

  protected AdvancedAchievementsTotalRequirement(Requirement clone) {
    super(clone);
  }

  @Override
  public double getProgress(Player player) {
    return AdvancedAchievementsAPIFetcher.fetchInstance().get().getPlayerTotalAchievements(player.getUniqueId());
  }

  @Override
  public Requirement clone() {
    return new AdvancedAchievementsTotalRequirement(this);
  }
}
