package sh.okx.rankup.requirements.requirement.votingplugin;

import com.Ben12345rocks.VotingPlugin.UserManager.UserManager;
import org.bukkit.entity.Player;
import sh.okx.rankup.Rankup;
import sh.okx.rankup.requirements.Requirement;
import sh.okx.rankup.requirements.ProgressiveRequirement;

public class VotingPluginVotesRequirement extends ProgressiveRequirement {
  public VotingPluginVotesRequirement(Rankup plugin) {
    super(plugin, "votingplugin-votes");
  }

  protected VotingPluginVotesRequirement(VotingPluginVotesRequirement clone) {
    super(clone);
  }

  @Override
  public double getProgress(Player player) {
    return UserManager.getInstance().getVotingPluginUser(player).getPoints();
  }

  @Override
  public Requirement clone() {
    return new VotingPluginVotesRequirement(this);
  }
}
