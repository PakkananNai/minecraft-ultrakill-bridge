package net.pakkanannai.mcfabricmod;

import java.nio.ByteBuffer;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL32;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Asynchronous OpenGL PBO readback and worker-thread publication into the shared framebuffer. */
public final class MinecraftFramebufferCapture implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger("MinecraftBridge");
    private static final int PBO_COUNT = SharedFramebuffer.SLOT_COUNT;
    private static final boolean CAPTURE_WITHOUT_WORLD = Boolean.getBoolean("minecraft.ultrakill.bridge.captureWithoutWorld");
    private static final int MAX_WIDTH = SharedFramebuffer.WIDTH;
    private static final int MAX_HEIGHT = SharedFramebuffer.HEIGHT;
    private static final int MAX_BYTES = SharedFramebuffer.SLOT_CAPACITY;
    private static final long NON_BLOCKING_WAIT_NS = 0L;

    private final GuestControlClient controlClient;
    private final PboSlot[] slots = new PboSlot[PBO_COUNT];
    private final ExecutorService publisher = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "MinecraftBridge-FramePublisher");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicBoolean closed = new AtomicBoolean();
    private volatile long submittedFrames;
    private volatile long droppedFrames;
    private boolean warnedOversize;
    private boolean glInitialized;

    public MinecraftFramebufferCapture(GuestControlClient controlClient) {
        this.controlClient = controlClient;
        for (int i = 0; i < slots.length; i++) slots[i] = new PboSlot();
        if (CAPTURE_WITHOUT_WORLD)
            LOGGER.warn("Diagnostic capture override enabled: framebuffer readback will also run outside a world");
    }

    /** Must be called on Minecraft's render thread while its OpenGL context is current. */
    public void capture(MinecraftClient client) {
        if (closed.get() || !client.isOnThread()) return;
        try {
            initializeGlResources();
            pollCompletedReadbacks();
            if ((client.world != null || CAPTURE_WITHOUT_WORLD) && controlClient.hasValidatedFramebuffer())
                submitReadback(client.getFramebuffer());
        } catch (RuntimeException error) {
            LOGGER.warn("Framebuffer capture failed; the next frame will retry", error);
        }
    }

    private void submitReadback(Framebuffer framebuffer) {
        int width = framebuffer.textureWidth;
        int height = framebuffer.textureHeight;
        long byteCount = (long) width * height * SharedFramebuffer.BYTES_PER_PIXEL;
        if (width < 1 || height < 1 || width > MAX_WIDTH || height > MAX_HEIGHT || byteCount > MAX_BYTES) {
            if (!warnedOversize) {
                LOGGER.warn("Framebuffer dimensions {}x{} exceed shared-buffer capacity {}x{}; capture paused",
                        width, height, MAX_WIDTH, MAX_HEIGHT);
                warnedOversize = true;
            }
            return;
        }
        warnedOversize = false;

        PboSlot pbo = findFreePbo();
        if (pbo == null) { droppedFrames++; return; }

        GuestControlClient.CaptureReservation reservation = controlClient.reserveCaptureSlot();
        if (reservation == null) { droppedFrames++; return; }
        int previousReadFramebuffer = 0;
        int previousReadBuffer = 0;
        int previousPackBuffer = 0;
        int previousPackAlignment = 4;
        int previousPackRowLength = 0;
        int previousPackSkipRows = 0;
        int previousPackSkipPixels = 0;
        boolean stateCaptured = false;
        boolean queued = false;
        try {
            previousReadFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
            previousReadBuffer = GL11.glGetInteger(GL11.GL_READ_BUFFER);
            previousPackBuffer = GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING);
            previousPackAlignment = GL11.glGetInteger(GL11.GL_PACK_ALIGNMENT);
            previousPackRowLength = GL11.glGetInteger(GL11.GL_PACK_ROW_LENGTH);
            previousPackSkipRows = GL11.glGetInteger(GL11.GL_PACK_SKIP_ROWS);
            previousPackSkipPixels = GL11.glGetInteger(GL11.GL_PACK_SKIP_PIXELS);
            stateCaptured = true;
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, framebuffer.fbo);
            GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, pbo.id);
            if (pbo.allocatedBytes != byteCount) {
                GL15.glBufferData(GL21.GL_PIXEL_PACK_BUFFER, byteCount, GL15.GL_STREAM_READ);
                pbo.allocatedBytes = byteCount;
            }
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
            GL11.glPixelStorei(GL11.GL_PACK_ROW_LENGTH, 0);
            GL11.glPixelStorei(GL11.GL_PACK_SKIP_ROWS, 0);
            GL11.glPixelStorei(GL11.GL_PACK_SKIP_PIXELS, 0);
            GL11.glReadPixels(0, 0, width, height, GL12.GL_BGRA, GL11.GL_UNSIGNED_BYTE, 0L);
            long fence = GL32.glFenceSync(GL32.GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
            if (fence == 0L) throw new IllegalStateException("OpenGL did not create a readback fence");
            pbo.fence = fence;
            pbo.reservation = reservation;
            pbo.width = width;
            pbo.height = height;
            pbo.stride = width * SharedFramebuffer.BYTES_PER_PIXEL;
            pbo.byteCount = (int) byteCount;
            pbo.state = PboState.GPU_PENDING;
            queued = true;
            submittedFrames++;
        } finally {
            try {
                if (stateCaptured) {
                    GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, previousPackBuffer);
                    GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, previousPackAlignment);
                    GL11.glPixelStorei(GL11.GL_PACK_ROW_LENGTH, previousPackRowLength);
                    GL11.glPixelStorei(GL11.GL_PACK_SKIP_ROWS, previousPackSkipRows);
                    GL11.glPixelStorei(GL11.GL_PACK_SKIP_PIXELS, previousPackSkipPixels);
                    GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousReadFramebuffer);
                    GL11.glReadBuffer(previousReadBuffer);
                }
            } finally {
                if (!queued) controlClient.cancelCapture(reservation);
            }
        }
    }

    private void pollCompletedReadbacks() {
        for (PboSlot pbo : slots) {
            if (pbo.state == PboState.GPU_PENDING) {
                int result = GL32.glClientWaitSync(pbo.fence, 0, NON_BLOCKING_WAIT_NS);
                if (result == GL32.GL_ALREADY_SIGNALED || result == GL32.GL_CONDITION_SATISFIED) {
                    GL32.glDeleteSync(pbo.fence);
                    pbo.fence = 0L;
                    try {
                        mapAndPublish(pbo);
                    } catch (RuntimeException failure) {
                        if (pbo.state == PboState.GPU_PENDING) {
                            controlClient.cancelCapture(pbo.reservation);
                            reset(pbo);
                            droppedFrames++;
                        }
                        throw failure;
                    }
                } else if (result == GL32.GL_WAIT_FAILED) {
                    GL32.glDeleteSync(pbo.fence);
                    pbo.fence = 0L;
                    controlClient.cancelCapture(pbo.reservation);
                    reset(pbo);
                    droppedFrames++;
                }
            } else if (pbo.state == PboState.CPU_COPYING && pbo.copyFuture.isDone()) {
                finishCopy(pbo);
            }
        }
    }

    private void mapAndPublish(PboSlot pbo) {
        int previousPackBuffer = GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING);
        ByteBuffer mapped;
        try {
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, pbo.id);
            mapped = GL15.glMapBuffer(GL21.GL_PIXEL_PACK_BUFFER, GL15.GL_READ_ONLY);
        } finally {
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, previousPackBuffer);
        }
        if (mapped == null || mapped.remaining() < pbo.byteCount) {
            if (mapped != null) unmap(pbo);
            controlClient.cancelCapture(pbo.reservation);
            reset(pbo);
            droppedFrames++;
            return;
        }
        mapped.limit(pbo.byteCount);
        pbo.mappedBuffer = mapped.asReadOnlyBuffer();
        try {
            pbo.copyFuture = publisher.submit(() -> {
                try {
                    controlClient.publishCapturedFrame(pbo.reservation, pbo.width, pbo.height, pbo.stride,
                            pbo.mappedBuffer.asReadOnlyBuffer());
                } catch (RuntimeException error) {
                    controlClient.cancelCapture(pbo.reservation);
                    throw error;
                }
            });
            pbo.state = PboState.CPU_COPYING;
        } catch (RuntimeException rejected) {
            unmap(pbo);
            controlClient.cancelCapture(pbo.reservation);
            reset(pbo);
            throw rejected;
        }
    }

    private void finishCopy(PboSlot pbo) {
        try {
            pbo.copyFuture.get();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            LOGGER.warn("Interrupted while collecting framebuffer publication", interrupted);
        } catch (ExecutionException failed) {
            LOGGER.warn("Could not publish captured framebuffer", failed.getCause());
        } finally {
            unmap(pbo);
            reset(pbo);
        }
    }

    private void unmap(PboSlot pbo) {
        int previousPackBuffer = GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING);
        try {
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, pbo.id);
            if (!GL15.glUnmapBuffer(GL21.GL_PIXEL_PACK_BUFFER))
                LOGGER.warn("OpenGL reported corrupted pixel-pack buffer contents");
        } finally {
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, previousPackBuffer);
        }
    }

    private PboSlot findFreePbo() {
        for (PboSlot slot : slots) if (slot.state == PboState.FREE) return slot;
        return null;
    }

    private static void reset(PboSlot pbo) {
        pbo.state = PboState.FREE;
        pbo.reservation = null;
        pbo.mappedBuffer = null;
        pbo.copyFuture = null;
        pbo.width = pbo.height = pbo.stride = pbo.byteCount = 0;
    }

    public long submittedFrames() { return submittedFrames; }
    public long droppedFrames() { return droppedFrames; }

    /** Must be called on the render thread before the OpenGL context is destroyed. */
    public void initializeGlResources() {
        if (closed.get() || glInitialized) return;
        int[] generated = new int[slots.length];
        int count = 0;
        try {
            for (; count < generated.length; count++) {
                generated[count] = GL15.glGenBuffers();
                if (generated[count] == 0) throw new IllegalStateException("OpenGL failed to allocate pixel-pack buffer");
            }
            for (int i = 0; i < slots.length; i++) slots[i].id = generated[i];
            glInitialized = true;
        } catch (RuntimeException failure) {
            for (int id : generated) if (id != 0) GL15.glDeleteBuffers(id);
            throw failure;
        }
    }

    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
        publisher.shutdown();
        boolean interrupted = false;
        try {
            // A worker may still be reading a mapped PBO. Do not unmap/delete it until the
            // worker has completed, even if client shutdown takes a little longer.
            while (!publisher.awaitTermination(1, TimeUnit.SECONDS))
                LOGGER.warn("Waiting for the final framebuffer copy before releasing mapped PBOs");
        } catch (InterruptedException signal) {
            interrupted = true;
            publisher.shutdownNow();
            boolean terminated = false;
            while (!terminated) {
                try { terminated = publisher.awaitTermination(1, TimeUnit.SECONDS); }
                catch (InterruptedException again) { interrupted = true; }
            }
        } finally {
            if (interrupted) Thread.currentThread().interrupt();
        }
        for (PboSlot slot : slots) {
            if (slot.fence != 0L) GL32.glDeleteSync(slot.fence);
            if (slot.state == PboState.CPU_COPYING && slot.mappedBuffer != null) unmap(slot);
            if (slot.id != 0) GL15.glDeleteBuffers(slot.id);
            if (slot.reservation != null) controlClient.cancelCapture(slot.reservation);
            reset(slot);
            slot.id = 0;
            slot.allocatedBytes = 0;
        }
    }

    private enum PboState { FREE, GPU_PENDING, CPU_COPYING }

    private static final class PboSlot {
        int id;
        long allocatedBytes;
        long fence;
        PboState state = PboState.FREE;
        GuestControlClient.CaptureReservation reservation;
        ByteBuffer mappedBuffer;
        Future<?> copyFuture;
        int width, height, stride, byteCount;
    }
}
