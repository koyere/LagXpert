package me.koyere.lagxpert.api.events;

import org.bukkit.Chunk;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Base class for all LagXpert-related events.
 */
public abstract class LagEvent extends Event {

    private static final HandlerList handlers = new HandlerList();
    private final Chunk chunk;

    public LagEvent(Chunk chunk) {
        this.chunk = chunk;
    }

    public Chunk getChunk() {
        return chunk;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
