package dev.gigaherz.hudcompass.icons;

import dev.gigaherz.hudcompass.HudCompass;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

public class BasicIconData implements IIconData<BasicIconData>
{
    public static final BasicIconData MISSING_ICON = new BasicIconData(HudCompass.POI_SERIALIZER.get(), 4);

    public static BasicIconData poi(int index)
    {
        return new BasicIconData(HudCompass.POI_SERIALIZER.get(), index);
    }

    public static BasicIconData mapMarker(int index)
    {
        return new BasicIconData(HudCompass.MAP_MARKER_SERIALIZER.get(), index);
    }

    private final IconDataSerializer<BasicIconData> serializer;
    public final int iconIndex;
    public float r = 1.0f;
    public float g = 1.0f;
    public float b = 1.0f;
    public float a = 1.0f;

    public BasicIconData(IconDataSerializer<BasicIconData> serializer, int iconIndex)
    {
        this.serializer = serializer;
        this.iconIndex = iconIndex;
    }

    public void setColor(float r, float g, float b, float a)
    {
        this.r = r;
        this.g = g;
        this.b = b;
        this.a = a;
    }

    @Override
    public IconDataSerializer<BasicIconData> getSerializer()
    {
        return serializer;
    }

    public static class Serializer extends IconDataSerializer<BasicIconData>
    {
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
