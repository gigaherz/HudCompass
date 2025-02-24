package dev.gigaherz.hudcompass.integrations.curios;

import dev.gigaherz.hudcompass.HudCompass;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.fml.InterModComms;
import net.minecraftforge.fml.event.lifecycle.InterModEnqueueEvent;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotTypeMessage;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;

import java.util.Optional;

public class CuriosSlotIntegration {
    public static final String SLOT_ID = "compass_slot";

    public static void register(final InterModEnqueueEvent event) {
        InterModComms.sendTo(CuriosApi.MODID, SlotTypeMessage.REGISTER_TYPE,
                () -> new SlotTypeMessage.Builder(SLOT_ID)
                        .size(1)
                        .icon(new ResourceLocation(HudCompass.MODID, "item/empty_compass_slot")) // You can create this texture
                        .priority(220)
                        .build()
        );
    }

    public static boolean canEquipCompass(LivingEntity entity) {
        Optional<ICuriosItemHandler> handler = CuriosApi.getCuriosHelper().getCuriosHandler(entity).resolve();

        return handler.map(h ->
                h.getStacksHandler(SLOT_ID)
                        .map(stacksHandler ->
                                stacksHandler.getSlots() > 0 &&
                                        stacksHandler.getStacks().getStackInSlot(0).isEmpty()
                        ).orElse(false)
        ).orElse(false);
    }

    public static int getCompassSlots(LivingEntity entity) {
        return CuriosApi.getCuriosHelper().getCuriosHandler(entity)
                .map(handler -> handler.getStacksHandler(SLOT_ID)
                        .map(stacksHandler -> stacksHandler.getSlots())
                        .orElse(0))
                .orElse(0);
    }

    public static void unlockCompassSlot(LivingEntity entity) {
        CuriosApi.getCuriosHelper().getCuriosHandler(entity).ifPresent(handler -> {
            handler.getStacksHandler(SLOT_ID).ifPresent(stacksHandler -> {
                if (stacksHandler.getSlots() == 0) {
                    handler.growSlotType(SLOT_ID, 1);
                }
            });
        });
    }
}