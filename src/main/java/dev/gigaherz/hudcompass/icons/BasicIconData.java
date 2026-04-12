package dev.gigaherz.hudcompass.icons;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.gigaherz.hudcompass.HudCompass;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.client.model.block.CompositeBlockModel;

public class BasicIconData implements IIconData<BasicIconData>
{
    public static final MapCodec<BasicIconData> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                    Identifier.CODEC.fieldOf("sprite").forGetter(e -> e.spriteName),
                    Codec.FLOAT.fieldOf("r").forGetter(e -> e.r),
                    Codec.FLOAT.fieldOf("g").forGetter(e -> e.g),
                    Codec.FLOAT.fieldOf("b").forGetter(e -> e.b),
                    Codec.FLOAT.fieldOf("a").forGetter(e -> e.a)
            ).apply(instance, BasicIconData::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, BasicIconData> STREAM_CODEC = StreamCodec.composite(
            Identifier.STREAM_CODEC, (e -> e.spriteName),
            ByteBufCodecs.FLOAT, (e -> e.r),
            ByteBufCodecs.FLOAT, (e -> e.g),
            ByteBufCodecs.FLOAT, (e -> e.b),
            ByteBufCodecs.FLOAT, (e -> e.a),
            BasicIconData::new
    );

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
    public final float r;
    public final float g;
    public final float b;
    public final float a;

    public BasicIconData(Identifier spriteName)
    {
        this(HudCompass.BASIC_SERIALIZER.get(), spriteName);
    }

    public BasicIconData(IconDataSerializer<BasicIconData> serializer, Identifier spriteName)
    {
        this(serializer, spriteName, 1,1,1,1);
    }

    private BasicIconData(Identifier spriteName, float r, float g, float b, float a)
    {
        this(HudCompass.BASIC_SERIALIZER.get(), spriteName, r, g, b, a);
    }

    public BasicIconData(IconDataSerializer<BasicIconData> serializer, Identifier spriteName, float r, float g, float b, float a)
    {
        this.serializer = serializer;
        this.spriteName = spriteName;
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
}
