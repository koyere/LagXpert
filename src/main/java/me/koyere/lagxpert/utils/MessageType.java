package me.koyere.lagxpert.utils;

/**
 * Enumeration for different message delivery methods supported by the plugin.
 * This allows for flexible message delivery based on user preferences and message context.
 */
public enum MessageType {
    /**
     * Send message to player's chat.
     * This is the traditional method and default behavior.
     */
    CHAT,
    
    /**
     * Send message to player's action bar.
     * Best for brief notifications and status updates.
     */
    ACTIONBAR,
    
    /**
     * Send message to both chat and action bar.
     * Useful for important notifications that need visibility.
     */
    BOTH
}