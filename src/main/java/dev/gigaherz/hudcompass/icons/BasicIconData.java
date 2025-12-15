package dev.gigaherz.hudcompass.icons;

import dev.gigaherz.hudcompass.HudCompass;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

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
        return new BasicIconData(HudCompass.BASIC_SERIALIZER.get(), Identifier.parse(spriteName));
    }

    public static BasicIconData basic(Identifier spriteName)
    {
        return new BasicIconData(HudCompass.BASIC_SERIALIZER.get(), spriteName);
    }

    private final IconDataSerializer<BasicIconData> serializer;
    public final Identifier spriteName;
    public float r = 1.0f;
    public float g = 1.0f;
    public float b = 1.0f;
    public float a = 1.0f;

    public BasicIconData(IconDataSerializer<BasicIconData> serializer, Identifier spriteName)
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
        public void write(BasicIconData data, ValueOutput output)
        {
            output.putString("SpriteName", data.spriteName.toString());
        }

        @Override
        public BasicIconData read(ValueInput input)
        {
            return new BasicIconData(
                    this,
                    Identifier.parse(input.getString("SpriteName").orElseThrow())
            );
        }

        @Override
        public void write(BasicIconData data, FriendlyByteBuf buffer)
        {
            buffer.writeIdentifier(data.spriteName);
        }

        @Override
        public BasicIconData read(FriendlyByteBuf buffer)
        {
            return new BasicIconData(
                    this,
                    buffer.readIdentifier()
            );
        }
    }
}
