package net.pakkanannai.mcfabricmod;

import com.bridge.minecraft.protocol.Messages;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Minecraft-client-thread implementation of host-requested block targeting. */
public final class GuestRaycastBridge {
    private static final Logger LOGGER = LoggerFactory.getLogger("MinecraftBridge");
    private final ConcurrentLinkedQueue<Messages.RaycastRequestMessage> queue = new ConcurrentLinkedQueue<>();
    private GuestControlClient controlClient;
    private int responses;

    public void setControlClient(GuestControlClient controlClient) {
        this.controlClient = controlClient;
    }

    public void enqueue(Messages.RaycastRequestMessage request) {
        if (request.getMaxDistance() <= 0.0f || request.getMaxDistance() > 64.0f) return;
        queue.add(request);
    }

    public void tick(MinecraftClient client) {
        Messages.RaycastRequestMessage request;
        while ((request = queue.poll()) != null) raycast(client, request);
    }

    private void raycast(MinecraftClient client, Messages.RaycastRequestMessage request) {
        if (client.world == null || client.getCameraEntity() == null) return;
        Vec3d start = client.getCameraEntity().getCameraPosVec(1.0f);
        Vec3d direction = client.getCameraEntity().getRotationVec(1.0f).normalize();
        Vec3d end = start.add(direction.multiply(request.getMaxDistance()));
        BlockHitResult result = client.world.raycast(new RaycastContext(
                start, end, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, client.getCameraEntity()));

        boolean hit = result.getType() == net.minecraft.util.hit.HitResult.Type.BLOCK;
        BlockPos pos = hit ? result.getBlockPos() : BlockPos.ORIGIN;
        String blockId = hit ? Registries.BLOCK.getId(client.world.getBlockState(pos).getBlock()).toString() : "";
        Vec3d hitPos = result.getPos();
        double distance = hitPos.distanceTo(start);
        Messages.RaycastResponseMessage response = new Messages.RaycastResponseMessage(
                request.getRequestId(), hit,
                pos.getX(), pos.getY(), pos.getZ(),
                hit ? result.getSide().getId() : 0,
                hitPos.x, hitPos.y, hitPos.z,
                (float) distance, blockId);
        if (controlClient != null) controlClient.sendRaycastResponse(response);
        if ((++responses % 30) == 0) {
            LOGGER.info("M9_RAYCAST_RESPONSE_SENT count={} request={} hit={} block={} pos={} distance={}",
                    responses, request.getRequestId(), hit, blockId, pos, String.format("%.3f", distance));
        }
    }
}
