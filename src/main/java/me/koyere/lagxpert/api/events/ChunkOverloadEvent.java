package me.koyere.lagxpert.api.events;

import org.bukkit.Chunk;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList; // Required import for Bukkit event system

/**
 * Event fired when a chunk exceeds one or more configured lag-contributing limits
 * (e.g., too many mobs, hoppers, etc.).
 * This event is cancellable, though cancellation behavior depends on what listens to it;
 * LagXpert itself might not alter its core logic based on cancellation by default.
 */
public class ChunkOverloadEvent extends LagEvent implements Cancellable {

    private static final HandlerList handlers = new HandlerList(); // Bukkit standard event handlers
    private boolean cancelled;
    private final String cause; // Describes what element caused the overload (e.g., "mobs", "hoppers")

    /**
     * Constructs a new ChunkOverloadEvent.
     *
     * @param overloadedChunk The chunk that is considered overloaded.
     * @param cause           A string identifier representing the primary reason for the overload
     * (e.g., "mobs", "hoppers", "redstone_timeout").
     */
    public ChunkOverloadEvent(Chunk overloadedChunk, String cause) {
        super(overloadedChunk); // Call the constructor of the parent LagEvent
        this.cause = cause;
        this.cancelled = false; // Initialize as not cancelled by default
    }

    /**
     * Gets the primary cause or type of element that triggered the overload condition.
     * Examples: "mobs", "hoppers", "redstone_timeout", "chests_scan_overload".
     *
     * @return A string representing the cause of the overload.
     */
    public String getCause() {
        return cause;
    }

    /**
     * Gets the cancellation state of this event.
     *
     * @return true if this event is cancelled.
     */
    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    /**
     * Sets the cancellation state of this event.
     *
     * @param cancel true if you wish to cancel this event.
     */
    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    /**
     * Gets the HandlerList for this event. Required by Bukkit event system.
     *
     * @return The HandlerList.
     */
    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    /**
     * Static method to get the HandlerList. Required by Bukkit event system.
     *
     * @return The HandlerList.
     */
    public static HandlerList getHandlerList() {
        return handlers;
    }
}