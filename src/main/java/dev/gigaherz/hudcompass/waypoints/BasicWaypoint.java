package dev.gigaherz.hudcompass.waypoints;

import com.mojang.serialization.Codec;
import dev.gigaherz.hudcompass.HudCompass;
import dev.gigaherz.hudcompass.icons.IIconData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.Objects;

public class BasicWaypoint extends PointInfo<BasicWaypoint>
{
    private String label;

    private Vec3 position;

    public BasicWaypoint()
    {
        super(HudCompass.BASIC_WAYPOINT.get(), false);
    }

    public BasicWaypoint(BlockPos exactPosition, @Nullable String label, IIconData<?> iconData)
    {
        this(Vec3.atCenterOf(exactPosition), label, iconData);
    }

    public BasicWaypoint(Vec3 exactPosition, @Nullable String label, IIconData<?> iconData)
    {
        this(HudCompass.BASIC_WAYPOINT.get(), exactPosition, label, iconData);
    }

    public BasicWaypoint(PointInfoType<BasicWaypoint> type, Vec3 exactPosition, @Nullable String label, IIconData<?> iconData)
    {
        super(type, false, label == null ? null : Component.literal(label), iconData);
        this.position = exactPosition;
        this.label = label == null ? "" : label;
    }

    @Override
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

    public String getLabelText()
    {
        return label;
    }

    public void setLabelText(String label)
    {
        this.label = Objects.requireNonNull(label);
        super.setLabel(label.length() > 0 ? Component.literal(label) : null);
    }

    @Override
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
    }
}
