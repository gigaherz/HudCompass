package dev.gigaherz.hudcompass.waypoints;

import com.mojang.datafixers.Products;
import com.mojang.datafixers.util.*;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.gigaherz.hudcompass.icons.IIconData;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

public abstract class PointInfo<T extends PointInfo<T>>
{
    public static final MapCodec<PointInfo<?>>
            DISPATCH_CODEC = PointInfoType.BY_ID_CODEC.dispatchMap(PointInfo::getType, PointInfoType::codec);

    public static final StreamCodec<RegistryFriendlyByteBuf, PointInfo<?>>
            DISPATCH_STREAM_CODEC = PointInfoType.BY_ID_STREAM_CODEC.dispatch(PointInfo::getType, PointInfoType::streamCodec);

    private final PointInfoType<? extends T> type;
    @Nullable
    private PointsOfInterest.WorldPoints owner;
    private UUID internalId;
    @Nullable
    private Component label;
    private IIconData<?> iconData;
    private boolean displayVerticalDistance = true;
    private boolean isServerProvided = true; // not used in the server
    private boolean isDynamic; // will not be saved to disk

    // For rendering purposes...
    public float fade;

    /* For SERVER side use */
    public PointInfo(PointInfoType<? extends T> type, boolean isDynamic)
    {
        this.isDynamic = isDynamic;
        this.type = type;
        this.internalId = UUID.randomUUID();
    }

    /* For CLIENT side use */
    public PointInfo(PointInfoType<T> type, boolean isDynamic, @Nullable Component label, IIconData<?> iconData)
    {
        this(type, isDynamic);
        this.label = label;
        this.iconData = iconData;
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    protected PointInfo(PointInfoType<T> type, boolean isDynamic, UUID internalId, Optional<Component>label, boolean displayVerticalDistance, IIconData<?> iconData)
    {
        this(type, isDynamic);
        this.displayVerticalDistance = displayVerticalDistance;
        this.internalId = internalId;
        this.label = label.orElse(null);
        this.iconData = iconData;
    }

    public PointInfoType<? extends T> getType()
    {
        return type;
    }

    @Nullable
    public final PointsOfInterest.WorldPoints getOwner()
    {
        return owner;
    }

    public UUID getInternalId()
    {
        return internalId;
    }

    public void setInternalId(UUID uuid)
    {
        internalId = uuid;
    }

    public abstract Vec3 getPosition(Player player, float partialTicks);

    @Nullable
    public Component getLabel()
    {
        return this.label;
    }

    public void setLabel(@Nullable Component text)
    {
        if (!Objects.equals(label, text))
            markDirty();
        this.label = text;
    }

    public IIconData<?> getIconData()
    {
        return iconData;
    }

    protected void setIconData(IIconData<?> iconData)
    {
        this.iconData = iconData;
    }

    @SuppressWarnings("unchecked")
    public T dynamic()
    {
        this.isDynamic = true;
        return (T) this;
    }

    @SuppressWarnings("unchecked")
    public final T noVerticalDistance()
    {
        this.displayVerticalDistance = false;
        return (T) this;
    }

    public boolean displayVerticalDistance(@Nullable Player player)
    {
        return displayVerticalDistance;
    }

    public void clientPoint()
    {
        isServerProvided = false;
    }

    public boolean isServerManaged()
    {
        return isServerProvided;
    }

    public boolean isDynamic()
    {
        return isDynamic;
    }

    public void tick(Player player)
    {
    }

    void setOwner(@Nullable PointsOfInterest.WorldPoints owner)
    {
        this.owner = owner;
    }

    public void markDirty()
    {
        if (owner != null)
        {
            owner.markDirty(this);
        }
    }

    private Fragment toFragment()
    {
        return new Fragment(
                internalId, Optional.ofNullable(label), displayVerticalDistance, iconData
        );
    }

    protected static <T extends PointInfo<T>> Products.P4<RecordCodecBuilder.Mu<T>, UUID, Optional<Component>, Boolean, IIconData<?>>
    codecFragment(RecordCodecBuilder.Instance<T> i)
    {
        return i.group(
                UUIDUtil.CODEC.fieldOf("ic").forGetter(PointInfo::getInternalId),
                ComponentSerialization.CODEC.optionalFieldOf("label").forGetter(e -> Optional.ofNullable(e.getLabel())),
                Codec.BOOL.fieldOf("displayVerticalDistance").forGetter(e -> e.displayVerticalDistance(null)),
                IIconData.DISPATCH_CODEC.fieldOf("icon").forGetter(PointInfo::getIconData)
        );
    }

    private record Fragment(UUID internalId, Optional<Component> label, Boolean displayVerticalDistance, IIconData<?> iconData){}
    private static final StreamCodec<RegistryFriendlyByteBuf, Fragment> FRAGMENT_STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, (e -> e.internalId),
            ByteBufCodecs.optional(ComponentSerialization.STREAM_CODEC), (e -> e.label),
            ByteBufCodecs.BOOL, (e -> e.displayVerticalDistance),
            IIconData.DISPATCH_STREAM_CODEC, (e -> e.iconData),
            Fragment::new
    );

    public static <B extends RegistryFriendlyByteBuf, C extends PointInfo<C>, T1>
    StreamCodec<B, C> streamCodec(StreamCodec<? super B, T1> codec1, Function<C, T1> getter1,
                                  Function5<UUID, Optional<Component>, Boolean, IIconData<?>, T1, C> constructor)
    {
        return StreamCodec.composite(
                FRAGMENT_STREAM_CODEC, PointInfo::toFragment,
                codec1, getter1,
                (f,v1) -> constructor.apply(f.internalId, f.label, f.displayVerticalDistance, f.iconData, v1)
        );
    }


    public static <B extends RegistryFriendlyByteBuf, C extends PointInfo<C>, T1, T2>
    StreamCodec<B, C> streamCodec(StreamCodec<? super B, T1> codec1, Function<C, T1> getter1,
                                  StreamCodec<? super B, T2> codec2, Function<C, T2> getter2,
                                  Function6<UUID, Optional<Component>, Boolean, IIconData<?>, T1, T2, C> constructor)
    {
        return StreamCodec.composite(
                FRAGMENT_STREAM_CODEC, PointInfo::toFragment,
                codec1, getter1,
                codec2, getter2,
                (f,v1, v2) -> constructor.apply(f.internalId, f.label, f.displayVerticalDistance, f.iconData, v1, v2)
        );
    }

    public static <B extends RegistryFriendlyByteBuf, C extends PointInfo<C>, T1, T2, T3>
    StreamCodec<B, C> streamCodec(StreamCodec<? super B, T1> codec1, Function<C, T1> getter1,
                                  StreamCodec<? super B, T2> codec2, Function<C, T2> getter2,
                                  StreamCodec<? super B, T3> codec3, Function<C, T3> getter3,
                                  Function7<UUID, Optional<Component>, Boolean, IIconData<?>, T1, T2, T3, C> constructor)
    {
        return StreamCodec.composite(
                FRAGMENT_STREAM_CODEC, PointInfo::toFragment,
                codec1, getter1,
                codec2, getter2,
                codec3, getter3,
                (f,v1, v2, v3) -> constructor.apply(f.internalId, f.label, f.displayVerticalDistance, f.iconData, v1, v2, v3)
        );
    }

    public static <B extends RegistryFriendlyByteBuf, C extends PointInfo<C>, T1, T2, T3, T4>
    StreamCodec<B, C> streamCodec(StreamCodec<? super B, T1> codec1, Function<C, T1> getter1,
                                  StreamCodec<? super B, T2> codec2, Function<C, T2> getter2,
                                  StreamCodec<? super B, T3> codec3, Function<C, T3> getter3,
                                  StreamCodec<? super B, T4> codec4, Function<C, T4> getter4,
                                  Function8<UUID, Optional<Component>, Boolean, IIconData<?>, T1, T2, T3, T4, C> constructor)
    {
        return StreamCodec.composite(
                FRAGMENT_STREAM_CODEC, PointInfo::toFragment,
                codec1, getter1,
                codec2, getter2,
                codec3, getter3,
                codec4, getter4,
                (f,v1, v2, v3, v4) -> constructor.apply(f.internalId, f.label, f.displayVerticalDistance, f.iconData, v1, v2, v3, v4)
        );
    }

    public static <B extends RegistryFriendlyByteBuf, C extends PointInfo<C>, T1, T2, T3, T4, T5>
    StreamCodec<B, C> streamCodec(StreamCodec<? super B, T1> codec1, Function<C, T1> getter1,
                                  StreamCodec<? super B, T2> codec2, Function<C, T2> getter2,
                                  StreamCodec<? super B, T3> codec3, Function<C, T3> getter3,
                                  StreamCodec<? super B, T4> codec4, Function<C, T4> getter4,
                                  StreamCodec<? super B, T5> codec5, Function<C, T5> getter5,
                                  Function9<UUID, Optional<Component>, Boolean, IIconData<?>, T1, T2, T3, T4, T5, C> constructor)
    {
        return StreamCodec.composite(
                FRAGMENT_STREAM_CODEC, PointInfo::toFragment,
                codec1, getter1,
                codec2, getter2,
                codec3, getter3,
                codec4, getter4,
                codec5, getter5,
                (f,v1, v2, v3, v4, v5) ->
                        constructor.apply(f.internalId, f.label, f.displayVerticalDistance, f.iconData, v1, v2, v3, v4, v5)
        );
    }

    public static <B extends RegistryFriendlyByteBuf, C extends PointInfo<C>, T1, T2, T3, T4, T5, T6>
    StreamCodec<B, C> streamCodec(StreamCodec<? super B, T1> codec1, Function<C, T1> getter1,
                                  StreamCodec<? super B, T2> codec2, Function<C, T2> getter2,
                                  StreamCodec<? super B, T3> codec3, Function<C, T3> getter3,
                                  StreamCodec<? super B, T4> codec4, Function<C, T4> getter4,
                                  StreamCodec<? super B, T5> codec5, Function<C, T5> getter5,
                                  StreamCodec<? super B, T6> codec6, Function<C, T6> getter6,
                                  Function10<UUID, Optional<Component>, Boolean, IIconData<?>, T1, T2, T3, T4, T5, T6, C> constructor)
    {
        return StreamCodec.composite(
                FRAGMENT_STREAM_CODEC, PointInfo::toFragment,
                codec1, getter1,
                codec2, getter2,
                codec3, getter3,
                codec4, getter4,
                codec5, getter5,
                codec6, getter6,
                (f,v1, v2, v3, v4, v5, v6) ->
                        constructor.apply(f.internalId, f.label, f.displayVerticalDistance, f.iconData, v1, v2, v3, v4, v5, v6)
        );
    }

    public static <B extends RegistryFriendlyByteBuf, C extends PointInfo<C>, T1, T2, T3, T4, T5, T6, T7>
    StreamCodec<B, C> streamCodec(StreamCodec<? super B, T1> codec1, Function<C, T1> getter1,
                                  StreamCodec<? super B, T2> codec2, Function<C, T2> getter2,
                                  StreamCodec<? super B, T3> codec3, Function<C, T3> getter3,
                                  StreamCodec<? super B, T4> codec4, Function<C, T4> getter4,
                                  StreamCodec<? super B, T5> codec5, Function<C, T5> getter5,
                                  StreamCodec<? super B, T6> codec6, Function<C, T6> getter6,
                                  StreamCodec<? super B, T7> codec7, Function<C, T7> getter7,
                                  Function11<UUID, Optional<Component>, Boolean, IIconData<?>, T1, T2, T3, T4, T5, T6, T7, C> constructor)
    {
        return StreamCodec.composite(
                FRAGMENT_STREAM_CODEC, PointInfo::toFragment,
                codec1, getter1,
                codec2, getter2,
                codec3, getter3,
                codec4, getter4,
                codec5, getter5,
                codec6, getter6,
                codec7, getter7,
                (f,v1, v2, v3, v4, v5, v6, v7) ->
                        constructor.apply(f.internalId, f.label, f.displayVerticalDistance, f.iconData, v1, v2, v3, v4, v5, v6, v7)
        );
    }

    public static <B extends RegistryFriendlyByteBuf, C extends PointInfo<C>, T1, T2, T3, T4, T5, T6, T7, T8>
    StreamCodec<B, C> streamCodec(StreamCodec<? super B, T1> codec1, Function<C, T1> getter1,
                                  StreamCodec<? super B, T2> codec2, Function<C, T2> getter2,
                                  StreamCodec<? super B, T3> codec3, Function<C, T3> getter3,
                                  StreamCodec<? super B, T4> codec4, Function<C, T4> getter4,
                                  StreamCodec<? super B, T5> codec5, Function<C, T5> getter5,
                                  StreamCodec<? super B, T6> codec6, Function<C, T6> getter6,
                                  StreamCodec<? super B, T7> codec7, Function<C, T7> getter7,
                                  StreamCodec<? super B, T8> codec8, Function<C, T8> getter8,
                                  Function12<UUID, Optional<Component>, Boolean, IIconData<?>, T1, T2, T3, T4, T5, T6, T7, T8, C> constructor)
    {
        return StreamCodec.composite(
                FRAGMENT_STREAM_CODEC, PointInfo::toFragment,
                codec1, getter1,
                codec2, getter2,
                codec3, getter3,
                codec4, getter4,
                codec5, getter5,
                codec6, getter6,
                codec7, getter7,
                codec8, getter8,
                (f,v1, v2, v3, v4, v5, v6, v7, v8) ->
                        constructor.apply(f.internalId, f.label, f.displayVerticalDistance, f.iconData, v1, v2, v3, v4, v5, v6, v7, v8)
        );
    }

}

