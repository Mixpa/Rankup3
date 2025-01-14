package sh.okx.rankup.requirements.requirement;

import org.bukkit.Material;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;
import sh.okx.rankup.Rankup;
import sh.okx.rankup.requirements.ProgressiveRequirement;
import sh.okx.rankup.requirements.Requirement;

public class BlockBreakRequirement extends ProgressiveRequirement {
  public BlockBreakRequirement(Rankup plugin) {
    super(plugin, "block-break", true);
  }

  protected BlockBreakRequirement(BlockBreakRequirement clone) {
    super(clone);
  }

  @Override
  public double getProgress(Player player) {
    Material material = Material.matchMaterial(getSub());
    if (material == null || !material.isBlock()) {
      throw new IllegalArgumentException("'" + getSub() + "' is not a valid block");
    }
    return player.getStatistic(Statistic.MINE_BLOCK, material);
  }

  @Override
  public Requirement clone() {
    return new BlockBreakRequirement(this);
  }
}
