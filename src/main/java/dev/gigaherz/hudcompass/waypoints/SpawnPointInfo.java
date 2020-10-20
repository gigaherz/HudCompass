package dev.gigaherz.hudcompass.waypoints;

import dev.gigaherz.hudcompass.icons.BasicIconData;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.registries.ObjectHolder;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class SpawnPointInfo extends BasicWaypoint
{
    @ObjectHolder("hudcompass:spawn")
    public static PointInfoType<SpawnPointInfo> TYPE = null;

    public static final UUID FIXED_HOME_ID = UUID.nameUUIDFromBytes("hudcompass:spawn".getBytes(StandardCharsets.UTF_8));

    public SpawnPointInfo()
    {
        super(TYPE, new Vector3d(0,0,0), "Home", BasicIconData.mapMarker(8));
        setInternalId(FIXED_HOME_ID);
    }

    @Override
    public void tick(PlayerEntity player)
    {
        super.tick(player);

        if (player.world instanceof ServerWorld)
        {
            BlockPos spawn = player.getBedPosition().orElseGet(() -> ((ServerWorld)player.world).getSpawnPoint());

            setPosition(new Vector3d(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5));
        }
    }
}
