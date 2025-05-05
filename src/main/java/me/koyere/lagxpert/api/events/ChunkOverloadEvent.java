package me.koyere.lagxpert.api.events;

import org.bukkit.Chunk;
import org.bukkit.event.Cancellable;

/**
 * Fired when a chunk exceeds one or more configured lag limits.
 */
public class ChunkOverloadEvent extends LagEvent implements Cancellable {

    private boolean cancelled;
    private final String cause;

    /**
     * @param chunk the chunk that is overloaded
     * @param cause string representing what caused the overload (e.g., "mobs", "hoppers")
     */
    public ChunkOverloadEvent(Chunk chunk, String cause) {
        super(chunk);
        this.cause = cause;
    }

    public String getCause() {
        return cause;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }
}
