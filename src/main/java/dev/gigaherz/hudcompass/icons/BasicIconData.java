package dev.gigaherz.hudcompass.icons;

import dev.gigaherz.hudcompass.HudCompass;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public class BasicIconData implements IIconData<BasicIconData>
{
    public static final BasicIconData MISSING_ICON = new BasicIconData(HudCompass.BASIC_SERIALIZER.get(), HudCompass.location("unknown"));

    public static BasicIconData generic()
    {
        return new BasicIconData(HudCompass.BASIC_SERIALIZER.get(), HudCompass.location("generic"));
    }

    public static BasicIconData basic(String spriteName)
    {
        return new BasicIconData(HudCompass.BASIC_SERIALIZER.get(), HudCompass.location(spriteName));
    }

    public static BasicIconData mapDecoration(String spriteName)
    {
        return new BasicIconData(HudCompass.BASIC_SERIALIZER.get(), new ResourceLocation(spriteName));
    }

    public static BasicIconData basic(ResourceLocation spriteName)
    {
        return new BasicIconData(HudCompass.BASIC_SERIALIZER.get(), spriteName);
    }

    private final IconDataSerializer<BasicIconData> serializer;
    public final ResourceLocation spriteName;
    public float r = 1.0f;
    public float g = 1.0f;
    public float b = 1.0f;
    public float a = 1.0f;

    public BasicIconData(IconDataSerializer<BasicIconData> serializer, ResourceLocation spriteName)
    {
        this.serializer = serializer;
        this.spriteName = spriteName;
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
            tag.putString("SpriteName", data.spriteName.toString());
            return tag;
        }

        @Override
        public BasicIconData read(CompoundTag tag)
        {
            return new BasicIconData(
                    this,
                    new ResourceLocation(tag.getString("SpriteName"))
            );
        }

        @Override
        public void write(BasicIconData data, FriendlyByteBuf buffer)
        {
            buffer.writeResourceLocation(data.spriteName);
        }

        @Override
        public BasicIconData read(FriendlyByteBuf buffer)
        {
            return new BasicIconData(
                    this,
                    buffer.readResourceLocation()
            );
        }
    }
}
