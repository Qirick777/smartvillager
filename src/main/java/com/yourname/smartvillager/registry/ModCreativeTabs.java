package com.yourname.smartvillager.registry;

import com.yourname.smartvillager.SmartVillagerMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * Deferred registry for the mod's creative-inventory tab, which collects all Smart Villager items.
 *
 * <p>Forge places mod-added tabs on a second page of the creative menu (reached with the tab-row
 * arrows), so the Village Core is <em>also</em> injected into the vanilla Functional Blocks tab for
 * immediate, first-page visibility.</p>
 */
public final class ModCreativeTabs {

    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, SmartVillagerMod.MOD_ID);

    private ModCreativeTabs() {
    }

    /** The Smart Villager creative tab. Its title key is {@code itemGroup.smartvillager}. */
    public static final RegistryObject<CreativeModeTab> SMARTVILLAGER_TAB =
            CREATIVE_MODE_TABS.register("smartvillager", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + SmartVillagerMod.MOD_ID))
                    .icon(() -> new ItemStack(ModItems.VILLAGE_CORE.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(ModItems.VILLAGE_CORE.get());
                    })
                    .build());

    /**
     * Attaches this registry to the given mod event bus.
     *
     * @param modEventBus the mod-specific event bus obtained in the {@code @Mod} constructor
     */
    public static void register(IEventBus modEventBus) {
        CREATIVE_MODE_TABS.register(modEventBus);
        modEventBus.addListener(ModCreativeTabs::onBuildTabContents);
    }

    /** Adds the Village Core to the vanilla Functional Blocks tab so it shows on the first page. */
    private static void onBuildTabContents(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(ModItems.VILLAGE_CORE.get());
        }
    }
}
