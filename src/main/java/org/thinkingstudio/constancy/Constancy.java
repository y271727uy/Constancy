package org.thinkingstudio.constancy;

import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.IExtensionPoint;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.forgespi.locating.IModFile;
import net.minecraftforge.network.NetworkConstants;
import net.minecraftforge.resource.PathPackResources;
import org.thinkingstudio.constancy.compat.ctm.CtmBackend;
import org.thinkingstudio.constancy.compat.ctm.GeneratedLegacyPack;
import org.thinkingstudio.constancy.compat.ctm.LegacyOptifineAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

@Mod("constancy")
public class Constancy {
    private static final String MOD_ID = "constancy";
    private static final Logger LOGGER = LoggerFactory.getLogger("Constancy");

    @SuppressWarnings("removal")
    public Constancy() {
        if (FMLLoader.getDist().isClient()) {
            ModLoadingContext.get().registerExtensionPoint(IExtensionPoint.DisplayTest.class,
                    () -> new IExtensionPoint.DisplayTest(() -> NetworkConstants.IGNORESERVERONLY, (a, b) -> true));
            IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
            modEventBus.addListener(Constancy::registerBuiltinPacks);
            modEventBus.register(LegacyOptifineAdapter.class);
            CtmBackend.initialize();
        }
    }

    private static void registerBuiltinPacks(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.CLIENT_RESOURCES) {
            return;
        }
        registerPack(event, "default", "Constancy Default Connected Textures");
        registerPack(event, "glass_pane_culling_fix", "Constancy Glass Pane Culling Fix");
        registerGeneratedPack(event);
    }

    private static void registerGeneratedPack(AddPackFindersEvent event) {
        event.addRepositorySource(consumer -> {
            Path root = FMLPaths.GAMEDIR.get().resolve("generated-ctm");
            GeneratedLegacyPack.prepare(root, FMLPaths.GAMEDIR.get().resolve("resourcepacks"));
            PathPackResources resources = new PathPackResources(MOD_ID + ":generated_ctm", true, root);
            consumer.accept(Pack.readMetaAndCreate(
                    MOD_ID + ":generated_ctm",
                    Component.literal("Constancy Generated Legacy CTM"),
                    true,
                    ignored -> resources,
                    PackType.CLIENT_RESOURCES,
                    Pack.Position.TOP,
                    PackSource.BUILT_IN));
        });
    }

    private static void registerPack(AddPackFindersEvent event, String folder, String title) {
        event.addRepositorySource(consumer -> {
            IModFile modFile = net.minecraftforge.fml.ModList.get().getModFileById(MOD_ID).getFile();
            Path root = modFile.findResource("resourcepacks", folder);
            PathPackResources resources = new PathPackResources(MOD_ID + ":" + folder, true, root);
            LOGGER.info("Registered built-in resource pack {} from {}", folder, root);
            consumer.accept(Pack.readMetaAndCreate(
                    MOD_ID + ":" + folder,
                    Component.literal(title),
                    false,
                    ignored -> resources,
                    PackType.CLIENT_RESOURCES,
                    Pack.Position.TOP,
                    PackSource.BUILT_IN));
        });
    }
}
