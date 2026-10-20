package dev.baranhan.viltrumitecore.hero.reinhard.cut;
import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerLevel;
import java.util.UUID;
public final class CutTelemetry {
   public static final boolean ENABLED=Boolean.getBoolean("viltrumitecore.reinhard.qa");
   private CutTelemetry() {}
   public static void done(String kind,UUID id,ServerLevel level,long elapsed,long nanos,WorldCutCarve carve) {
      if(ENABLED)LogUtils.getLogger().info("CUT_QA,DONE,{},{},{},{},{},{},{},{}",kind,id,elapsed,
         carve==null?-1:carve.firstTick(),carve==null?-1:carve.lastTick(),carve==null?0:carve.visited(),
         (System.nanoTime()-nanos)/1E6,level.getServer().getAverageTickTime());
   }
}
