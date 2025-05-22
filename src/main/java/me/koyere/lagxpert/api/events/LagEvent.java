package me.koyere.lagxpert.api.events;

import org.bukkit.Chunk;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList; // Still needed for the abstract getHandlers() return type

/**
 * Abstract base class for all LagXpert-related custom events.
 * Provides common functionality, such as associating an event with a specific Chunk.
 */
public abstract class LagEvent extends Event {

    protected final Chunk chunk; // Changed to protected for direct access by subclasses if preferred, or keep private with getter.

    /**
     * Constructor for LagEvent.
     *
     * @param chunk The chunk relevant to this event. Must not be null.
     */
    public LagEvent(Chunk chunk) {
        if (chunk == null) {
            throw new IllegalArgumentException("Chunk cannot be null for LagEvent");
        }
        this.chunk = chunk;
    }

    /**
     * Gets the chunk associated with this event.
     *
     * @return The Chunk relevant to this event.
     */
    public Chunk getChunk() {
        return chunk;
    }

    /**
     * {@inheritDoc}
     * <p>
     * This method is declared abstract in {@link org.bukkit.event.Event} and
     * must be implemented by concrete (non-abstract) subclasses of LagEvent.
     * Each concrete event subclass (e.g., ChunkOverloadEvent) will provide its own
     * static HandlerList instance.
     */
    @Override
    public abstract HandlerList getHandlers();
    // Note: Abstract event classes do not provide a static getHandlerList() method
    // or a static HandlerList field themselves. These are implemented by concrete event subclasses.

}