package me.pepperbell.continuity.client.mixin;

import java.util.Optional;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import me.pepperbell.continuity.client.config.ContinuityConfig;
import me.pepperbell.continuity.client.config.Option.IconChoice;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.resource.PathPackResources;
import net.minecraftforge.resource.ResourcePackLoader;

/** Keeps the page in the Constancy group while independently controlling its name and logo. */
@Mixin(targets = "org.embeddedt.embeddium.gui.frame.tab.TabHeaderWidget")
abstract class EmbeddiumTabHeaderWidgetMixin {
    @Redirect(
            method = "<init>",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraftforge/fml/ModList;getModContainerById(Ljava/lang/String;)Ljava/util/Optional;"))
    private Optional<? extends ModContainer> continuity$redirectLogoContainer(ModList modList, String modId) {
        if ("constancy".equals(modId)
                && ContinuityConfig.INSTANCE.configIcon.get() == IconChoice.CONTINUITY) {
            return modList.getModContainerById("continuity");
        }
        return modList.getModContainerById(modId);
    }

    @Redirect(
            method = "<init>",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraftforge/resource/ResourcePackLoader;getPackFor(Ljava/lang/String;)Ljava/util/Optional;"))
    private Optional<PathPackResources> continuity$redirectLogoPack(String modId) {
        if ("constancy".equals(modId)
                && ContinuityConfig.INSTANCE.configIcon.get() == IconChoice.CONTINUITY) {
            return ResourcePackLoader.getPackFor("continuity");
        }
        return ResourcePackLoader.getPackFor(modId);
    }

    @Inject(method = "getLabel", at = @At("HEAD"), cancellable = true)
    private static void continuity$overrideLabel(String modId, CallbackInfoReturnable<MutableText> cir) {
        if (!"constancy".equals(modId)) {
            return;
        }

        String key = ContinuityConfig.INSTANCE.configName.get() == IconChoice.CONTINUITY
                ? "options.continuity.config_name.continuity"
                : "options.continuity.config_name.constancy";
        cir.setReturnValue(Text.translatable(key));
    }
}
