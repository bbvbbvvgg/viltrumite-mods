package dev.baranhan.viltrumitecore.client;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.loading.FMLPaths;

public class CosmeticLoader {
   public static final Map<String, ResourceLocation> SKINS = new HashMap<>();
   public static final Map<String, ResourceLocation> CAPES = new HashMap<>();

   public static void init() {
      File gameDir = FMLPaths.GAMEDIR.get().toFile();
      File coreDir = new File(gameDir, "viltrumitetextures");
      File skinsDir = new File(coreDir, "skins");
      File capesDir = new File(coreDir, "capes");
      if (!skinsDir.exists()) {
         skinsDir.mkdirs();
      }

      if (!capesDir.exists()) {
         capesDir.mkdirs();
      }

      extractDefaultCosmetics(skinsDir, capesDir);
      loadFromFolder(skinsDir, SKINS, "skin");
      loadFromFolder(capesDir, CAPES, "cape");
   }

   // Every bundled skin the squad events use must be listed: Thula and Vidor were missing (already in the
   // original 1.10.3 jar), so their files were never extracted and they fell back to the default skin.
   private static void extractDefaultCosmetics(File skinsDir, File capesDir) {
      String[] defaultSkins = new String[]{"skin_lucan.png", "skin_omniman_1.png", "skin_omniman_2.png", "skin_conquest.png", "skin_viltrumite_1.png", "skin_thula.png", "skin_vidor.png"};
      String[] defaultCapes = new String[]{"red.png"};

      for (String skin : defaultSkins) {
         File targetFile = new File(skinsDir, skin);
         if (!targetFile.exists()) {
            extractFile("/assets/viltrumitecore/textures/entity/skins/" + skin, targetFile);
         }
      }

      for (String cape : defaultCapes) {
         File targetFile = new File(capesDir, cape);
         if (!targetFile.exists()) {
            extractFile("/assets/viltrumitecore/textures/entity/capes/" + cape, targetFile);
         }
      }
   }

   private static void extractFile(String resourcePath, File targetFile) {
      try (InputStream in = CosmeticLoader.class.getResourceAsStream(resourcePath)) {
         if (in != null) {
            Files.copy(in, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            System.out.println("[ViltrumiteCore] Default file extracted: " + targetFile.getName());
         } else {
            System.out.println("[ViltrumiteCore] WARNING: Default file not found in mod -> " + resourcePath);
         }
      } catch (Exception var7) {
         System.err.println("[ViltrumiteCore] Error extracting file: " + targetFile.getName());
         var7.printStackTrace();
      }
   }

   private static void loadFromFolder(File folder, Map<String, ResourceLocation> map, String type) {
      File[] files = folder.listFiles((dir, name) -> name.toLowerCase().endsWith(".png"));
      if (files != null && files.length != 0) {
         Minecraft client = Minecraft.getInstance();

         for (File file : files) {
            try (InputStream is = new FileInputStream(file)) {
               NativeImage image = NativeImage.read(is);
               DynamicTexture texture = new DynamicTexture(image);
               String rawName = file.getName().replace(".png", "");
               String safeName = rawName.toLowerCase().replaceAll("[^a-z0-9_.-]", "");
               ResourceLocation id = new ResourceLocation("viltrumitecore", "dynamic_" + type + "_" + safeName);
               client.getTextureManager().register(id, texture);
               map.put(rawName, id);
               System.out.println("[ViltrumiteCore] Dynamic " + type + " loaded: " + rawName);
            } catch (Exception var17) {
               System.err.println("[ViltrumiteCore] Error loading " + type + ": " + file.getName());
               var17.printStackTrace();
            }
         }
      }
   }
}
