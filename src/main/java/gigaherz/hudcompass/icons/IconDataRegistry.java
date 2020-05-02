package gigaherz.hudcompass.icons;

import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.registries.RegistryManager;

import javax.annotation.Nonnull;

public class IconDataRegistry
{
    public static IForgeRegistry<IconDataSerializer<?>> REGISTRY = RegistryManager.ACTIVE.getRegistry(IconDataSerializer.class);

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Nonnull
    public static CompoundNBT serializeIcon(@Nonnull IIconData<?> iconData)
    {
        IconDataSerializer serializer = iconData.getSerializer();
        ResourceLocation serializerId = serializer.getRegistryName();
        if(serializerId == null)
        {
            throw new IllegalStateException(String.format("Serializer name is null %s", serializer.getClass().getName()));
        }
        CompoundNBT tag = new CompoundNBT();
        tag.putString("Type", serializer.getRegistryName().toString());
        tag.put("Data", serializer.write(iconData));
        return tag;
    }

    @Nonnull
    public static IIconData<?> deserializeIcon(CompoundNBT tag)
    {
        ResourceLocation serializerId = new ResourceLocation(tag.getString("Type"));
        IconDataSerializer<?> serializer = REGISTRY.getValue(serializerId);
        if (serializer == null)
        {
            throw new IllegalStateException(String.format("Serializer not registered %s", serializerId));
        }
        return serializer.read(tag);
    }
}
