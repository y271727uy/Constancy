package me.pepperbell.continuity.client.config;

import com.google.common.collect.ImmutableList;
import me.jellysquid.mods.sodium.client.gui.options.OptionFlag;
import me.jellysquid.mods.sodium.client.gui.options.OptionGroup;
import me.jellysquid.mods.sodium.client.gui.options.OptionImpl;
import me.jellysquid.mods.sodium.client.gui.options.OptionPage;
import me.jellysquid.mods.sodium.client.gui.options.control.TickBoxControl;
import me.pepperbell.continuity.client.config.Option.BooleanOption;
import me.pepperbell.continuity.client.config.Option.IconChoice;
import me.jellysquid.mods.sodium.client.gui.options.control.CyclingControl;
import me.jellysquid.mods.sodium.client.gui.options.storage.OptionStorage;
import net.minecraft.text.Text;
import org.embeddedt.embeddium.api.OptionGUIConstructionEvent;
import org.embeddedt.embeddium.client.gui.options.OptionIdentifier;

/** Registers Constancy's settings in Embeddium's video settings screen. */
public final class ConstancyEmbeddiumOptions {
    private static final OptionIdentifier<Void> PAGE_ID = OptionIdentifier.create("constancy", "settings");
    private static final OptionStorageImpl STORAGE = new OptionStorageImpl();
    private static boolean registered;

    private ConstancyEmbeddiumOptions() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }

        OptionGUIConstructionEvent.BUS.addListener(ConstancyEmbeddiumOptions::addPage);
        registered = true;
    }

    private static void addPage(OptionGUIConstructionEvent event) {
        OptionGroup features = OptionGroup.createBuilder()
                .setId(OptionIdentifier.create("constancy", "features"))
                .add(configIconOption())
                .add(configNameOption())
                .add(booleanOption("connected_textures", ContinuityConfig.INSTANCE.connectedTextures,
                        OptionFlag.REQUIRES_RENDERER_RELOAD))
                .add(booleanOption("emissive_textures", ContinuityConfig.INSTANCE.emissiveTextures,
                        OptionFlag.REQUIRES_RENDERER_RELOAD))
                .add(booleanOption("custom_block_layers", ContinuityConfig.INSTANCE.customBlockLayers,
                        OptionFlag.REQUIRES_RENDERER_RELOAD))
                .add(booleanOption("use_manual_culling", ContinuityConfig.INSTANCE.useManualCulling,
                        OptionFlag.REQUIRES_RENDERER_RELOAD))
                .build();

        event.addPage(new OptionPage(
                PAGE_ID,
                Text.translatable("options.continuity.title"),
                ImmutableList.of(features)));
    }

    private static me.jellysquid.mods.sodium.client.gui.options.Option<IconChoice> configIconOption() {
        return OptionImpl.createBuilder(IconChoice.class, STORAGE)
                .setId(OptionIdentifier.create("constancy", "config_icon", IconChoice.class))
                .setName(Text.translatable("options.continuity.config_icon"))
                .setTooltip(Text.translatable("options.continuity.config_icon.tooltip"))
                .setControl(option -> new CyclingControl<>(option, IconChoice.class, new Text[]{
                        Text.translatable("options.continuity.config_icon.constancy"),
                        Text.translatable("options.continuity.config_icon.continuity")
                }))
                .setBinding((ignored, value) -> ContinuityConfig.INSTANCE.configIcon.set(value),
                        ignored -> ContinuityConfig.INSTANCE.configIcon.get())
                .build();
    }

    private static me.jellysquid.mods.sodium.client.gui.options.Option<IconChoice> configNameOption() {
        return OptionImpl.createBuilder(IconChoice.class, STORAGE)
                .setId(OptionIdentifier.create("constancy", "config_name", IconChoice.class))
                .setName(Text.translatable("options.continuity.config_name"))
                .setTooltip(Text.translatable("options.continuity.config_name.tooltip"))
                .setControl(option -> new CyclingControl<>(option, IconChoice.class, new Text[]{
                        Text.translatable("options.continuity.config_name.constancy"),
                        Text.translatable("options.continuity.config_name.continuity")
                }))
                .setBinding((ignored, value) -> ContinuityConfig.INSTANCE.configName.set(value),
                        ignored -> ContinuityConfig.INSTANCE.configName.get())
                .build();
    }

    private static me.jellysquid.mods.sodium.client.gui.options.Option<Boolean> booleanOption(
            String key,
            BooleanOption option,
            OptionFlag flag) {
        return OptionImpl.createBuilder(boolean.class, STORAGE)
                .setId(OptionIdentifier.create("constancy", key, Boolean.class))
                .setName(Text.translatable("options.continuity." + key))
                .setTooltip(Text.translatable("options.continuity." + key + ".tooltip"))
                .setControl(TickBoxControl::new)
                .setBinding((ignored, value) -> option.set(value), ignored -> option.get())
                .setFlags(flag)
                .build();
    }

    private static final class OptionStorageImpl implements OptionStorage<OptionStorageImpl> {
        @Override
        public OptionStorageImpl getData() {
            return this;
        }

        @Override
        public void save() {
            ContinuityConfig.INSTANCE.save();
        }
    }
}
