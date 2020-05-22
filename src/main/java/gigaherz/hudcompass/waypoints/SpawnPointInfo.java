package gigaherz.hudcompass.waypoints;

import gigaherz.hudcompass.icons.BasicIconData;
import net.minecraft.client.renderer.Vector3d;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.registries.ObjectHolder;
import org.apache.commons.io.Charsets;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class SpawnPointInfo extends BasicWaypoint
{
    @ObjectHolder("hudcompass:spawn")
    public static PointInfoType<SpawnPointInfo> TYPE = null;

    public static final UUID FIXED_HOME_ID = UUID.nameUUIDFromBytes("hudcompass:spawn".getBytes(StandardCharsets.UTF_8));

    public SpawnPointInfo()
    {
        super(TYPE, new Vec3d(0,0,0), "Home", BasicIconData.mapMarker(8));
        setInternalId(FIXED_HOME_ID);
    }

    @Override
    public void tick(PlayerEntity player)
    {
        super.tick(player);

        BlockPos spawn = player.getBedPosition().orElseGet(() -> player.world.getSpawnPoint());

        setPosition(new Vec3d(spawn.getX()+0.5, spawn.getY(), spawn.getZ()+0.5));
    }
}
