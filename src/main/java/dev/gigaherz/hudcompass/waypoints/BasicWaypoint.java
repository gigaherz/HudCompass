package dev.gigaherz.hudcompass.waypoints;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.gigaherz.hudcompass.HudCompass;
import dev.gigaherz.hudcompass.icons.IIconData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public class BasicWaypoint extends PointInfo<BasicWaypoint>
{
    public static final MapCodec<BasicWaypoint> CODEC = RecordCodecBuilder.mapCodec(instance ->
            codecFragment(instance).and(Vec3.CODEC.fieldOf("pos").forGetter(BasicWaypoint::getPosition)).apply(instance, BasicWaypoint::new)
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, BasicWaypoint> STREAM_CODEC = PointInfo.streamCodec(
            Vec3.STREAM_CODEC, BasicWaypoint::getPosition,
            BasicWaypoint::new
    );

    private Vec3 position;

    public BasicWaypoint(Vec3 exactPosition, @Nullable String label, IIconData<?> iconData)
    {
        this(HudCompass.BASIC_WAYPOINT.get(), exactPosition, label != null ? Component.literal(label) : null, iconData);
    }

    protected BasicWaypoint(PointInfoType<BasicWaypoint> type, Vec3 exactPosition, @Nullable String label, IIconData<?> iconData)
    {
        this(type, exactPosition, label != null ? Component.literal(label) : null, iconData);
    }

    protected BasicWaypoint(PointInfoType<BasicWaypoint> type, Vec3 exactPosition, @Nullable Component label, IIconData<?> iconData)
    {
        super(type, false, label, iconData);
        this.position = exactPosition;
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    protected BasicWaypoint(UUID internalId, Optional<Component> label, boolean displayVerticalDistance, IIconData<?> iconData, Vec3 exactPosition)
    {
        super(HudCompass.BASIC_WAYPOINT.get(), false, internalId, label, displayVerticalDistance, iconData);
        this.position = exactPosition;
    }

    public Vec3 getPosition()
    {
        return position;
    }

    @Override
    public Vec3 getPosition(Player player, float partialTicks)
    {
        return position;
    }

    public void setPosition(Vec3 position)
    {
        if (Mth.equal(position.distanceToSqr(position), 0))
        {
            this.position = position;
            markDirty();
        }
    }

    /*@Override
    protected void serializeAdditional(ValueOutput output)
    {
        output.putDouble("X", position.x);
        output.putDouble("Y", position.y);
        output.putDouble("Z", position.z);
        output.putString("Text", label);
    }

    @Override
    protected void deserializeAdditional(ValueInput input)
    {
        position = new Vec3(
                input.read("X", Codec.DOUBLE).orElseThrow(),
                input.read("Y", Codec.DOUBLE).orElseThrow(),
                input.read("Z", Codec.DOUBLE).orElseThrow()
        );
        label = input.getString("Text").orElseThrow();
    }

    @Override
    protected void serializeAdditional(FriendlyByteBuf buffer)
    {
        buffer.writeDouble(position.x);
        buffer.writeDouble(position.y);
        buffer.writeDouble(position.z);
        buffer.writeUtf(label, 1024);
    }

    @Override
    protected void deserializeAdditional(FriendlyByteBuf buffer)
    {
        position = new Vec3(
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readDouble()
        );
        label = buffer.readUtf(1024);
    }*/
}
