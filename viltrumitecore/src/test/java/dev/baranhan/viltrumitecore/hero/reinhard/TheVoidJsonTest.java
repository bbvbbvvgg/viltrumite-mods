package dev.baranhan.viltrumitecore.hero.reinhard;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import java.nio.file.*;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;
import net.minecraft.resources.RegistryOps;
import org.junit.jupiter.api.Test;

/** Real 1.20.1 codecs with registry data only: does not start a game/server/world. */
class TheVoidJsonTest {
   static JsonObject file(String path) throws Exception {return JsonParser.parseString(Files.readString(Path.of("src/main/resources/data/viltrumitecore/"+path+"/the_void.json"))).getAsJsonObject();}
   static void bootstrap(){SharedConstants.tryDetectVersion();Bootstrap.bootStrap();}
   @Test void dimensionTypeDecodes() throws Exception {var json=file("dimension_type");bootstrap();var result=DimensionType.DIRECT_CODEC.parse(JsonOps.INSTANCE,json);assertTrue(result.result().isPresent(),()->result.error().toString());var type=result.result().orElseThrow();assertEquals(0,type.minY());assertEquals(256,type.height());assertFalse(type.hasSkyLight());assertEquals("viltrumitecore:the_void",type.effectsLocation().toString());}
   @Test void missingRequiredFieldFailsRealCodec() throws Exception {var json=file("dimension_type");json.remove("monster_spawn_light_level");bootstrap();assertTrue(DimensionType.DIRECT_CODEC.parse(JsonOps.INSTANCE,json).error().isPresent());}
   @Test void flatSettingsDecode() throws Exception {var json=file("dimension");bootstrap();var ops=RegistryOps.create(JsonOps.INSTANCE,net.minecraft.data.registries.VanillaRegistries.createLookup());var result=FlatLevelGeneratorSettings.CODEC.parse(ops,json.getAsJsonObject("generator").get("settings"));assertTrue(result.result().isPresent(),()->result.error().toString());var settings=result.result().orElseThrow();assertEquals(1,settings.getLayersInfo().size());assertEquals(1,settings.getLayersInfo().get(0).getHeight());assertTrue(settings.getLayersInfo().get(0).getBlockState().isAir());assertEquals("minecraft:the_void",settings.getBiome().unwrapKey().orElseThrow().location().toString());assertFalse(json.getAsJsonObject("generator").getAsJsonObject("settings").get("features").getAsBoolean());assertTrue(json.getAsJsonObject("generator").getAsJsonObject("settings").getAsJsonArray("structure_overrides").isEmpty());}
}
