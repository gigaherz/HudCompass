package dev.gigaherz.hudcompass.icons;

import dev.gigaherz.hudcompass.HudCompass;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.registries.ObjectHolder;

public class BasicIconData implements IIconData<BasicIconData>
{
    @ObjectHolder(HudCompass.MODID + ":poi")
    public static IconDataSerializer<BasicIconData> POI_SERIALIZER = null;

    @ObjectHolder(HudCompass.MODID + ":map_marker")
    public static IconDataSerializer<BasicIconData> MAP_SERIALIZER = null;

    public static final BasicIconData MISSING_ICON = new BasicIconData(Serializer.POI_SERIALIZER, 4);

    public static IIconData<?> poi(int index)
    {
        return new BasicIconData(Serializer.POI_SERIALIZER, index);
    }
    public static IIconData<?> mapMarker(int index)
    {
        return new BasicIconData(Serializer.MAP_SERIALIZER, index);
    }

    private final IconDataSerializer<BasicIconData> serializer;
    public final int iconIndex;

    public BasicIconData(IconDataSerializer<BasicIconData> serializer, int iconIndex)
    {
        this.serializer = serializer;
        this.iconIndex = iconIndex;
    }

    @Override
    public IconDataSerializer<BasicIconData> getSerializer()
    {
        return serializer;
    }

    public static class Serializer extends IconDataSerializer<BasicIconData>
    {
        public static final Serializer POI_SERIALIZER = new Serializer();
        public static final Serializer MAP_SERIALIZER = new Serializer();

        @Override
        public CompoundTag write(BasicIconData data, CompoundTag tag)
        {
            tag.putInt("Index", data.iconIndex);
            return tag;
        }

        @Override
        public BasicIconData read(CompoundTag tag)
        {
            return new BasicIconData(
                    this,
                    tag.getInt("Index")
            );
        }

        @Override
        public void write(BasicIconData data, FriendlyByteBuf buffer)
        {
            buffer.writeInt(data.iconIndex);
        }

        @Override
        public BasicIconData read(FriendlyByteBuf buffer)
        {
            return new BasicIconData(
                    this,
                    buffer.readInt()
            );
        }
    }
}
