package dev.gigaherz.hudcompass.integrations.curios;

import dev.gigaherz.hudcompass.HudCompass;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.InterModComms;
import net.minecraftforge.fml.event.lifecycle.InterModEnqueueEvent;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotTypeMessage;

public class CuriosSlotIntegration {
    public static final String SLOT_ID = "compass";

    public static void register(final InterModEnqueueEvent event) {

        HudCompass.LOGGER.debug("Registering compass slot with Curios API");
        InterModComms.sendTo(CuriosApi.MODID, SlotTypeMessage.REGISTER_TYPE,
                () -> new SlotTypeMessage.Builder(SLOT_ID)
                        .size(1)
                        .icon(new ResourceLocation(HudCompass.MODID, "item/empty_compass_slot"))
                        .priority(220)
                        .build()
        );
    }
}