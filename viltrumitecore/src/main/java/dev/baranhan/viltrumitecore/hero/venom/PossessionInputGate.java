package dev.baranhan.viltrumitecore.hero.venom;

import dev.baranhan.viltrumitecore.compat.spider.SpiderCompat;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.ReferenceCountUtil;
import net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * While a Spider is driven, his own client must not fire his abilities through Falbiks' channel. Falbiks handles its
 * input packets without any hook of ours, so the server drops them in the host's own connection pipeline - nothing of
 * Falbiks is patched or mixed into. Installed for the takeover only, removed by every end path; a pipeline that
 * refuses the handler (a fake player, a closed channel) is logged once and the takeover still works (the client also
 * swallows those keys, VenomPossessionClient).
 */
public final class PossessionInputGate extends ChannelInboundHandlerAdapter {
   static final String NAME = "viltrumitecore_venom_gate";
   private static final Logger LOG = LoggerFactory.getLogger("viltrumitecore/venom");
   private static boolean warned;
   private final ResourceLocation channel;

   private PossessionInputGate(ResourceLocation channel) {
      this.channel = channel;
   }

   /** Pure: is this inbound message one of the gated channel's payloads. */
   static boolean gated(Object msg, ResourceLocation channel) {
      return msg instanceof ServerboundCustomPayloadPacket p && channel.equals(p.getIdentifier());
   }

   @Override
   public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
      if (gated(msg, this.channel)) {
         ReferenceCountUtil.release(msg);
         return;
      }
      super.channelRead(ctx, msg);
   }

   static void install(ServerPlayer host) {
      if (!SpiderCompat.present()) {
         return; // no Falbiks, no channel to gate
      }
      Channel ch = channel(host);
      if (ch == null) {
         return;
      }
      ResourceLocation id = ResourceLocation.tryParse(SpiderCompat.inputChannel());
      if (id == null) {
         return;
      }
      ch.eventLoop().execute(() -> {
         try {
            if (ch.pipeline().get(NAME) == null && ch.pipeline().get("packet_handler") != null) {
               ch.pipeline().addBefore("packet_handler", NAME, new PossessionInputGate(id));
            }
         } catch (RuntimeException e) {
            if (!warned) {
               warned = true;
               LOG.warn("Venom takeover: could not gate {} on {}'s connection ({}); the client still swallows the keys", id, host.getScoreboardName(),
                  e.toString());
            }
         }
      });
   }

   static void remove(ServerPlayer host) {
      Channel ch = channel(host);
      if (ch == null) {
         return;
      }
      ch.eventLoop().execute(() -> {
         try {
            if (ch.pipeline().get(NAME) != null) {
               ch.pipeline().remove(NAME);
            }
         } catch (RuntimeException ignored) {
            // the channel closed meanwhile: nothing left to gate
         }
      });
   }

   private static Channel channel(ServerPlayer host) {
      try {
         return host.connection == null || host.connection.connection == null ? null : host.connection.connection.channel();
      } catch (RuntimeException e) {
         return null;
      }
   }
}
