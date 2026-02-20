package com.smoky.bassshakertelemetry.telemetryout;

import com.smoky.bassshakertelemetry.api.HapticUnifiedEvent;

/**
 * Thread-local context for enriching outgoing unified events.
 *
 * <p>Call sites that know semantic IDs and positions (e.g., client ingress) can install a context
 * before triggering synthesis, allowing the engine-side emitter to broadcast a rich "event" JSON
 * packet without changing audio method signatures.
 */
public final class HapticEventContext {
    private static final ThreadLocal<HapticUnifiedEvent> CURRENT = new ThreadLocal<>();

    private HapticEventContext() {
    }

    public static void withEventContext(HapticUnifiedEvent event, Runnable action) {
        if (action == null) {
            return;
        }

        HapticUnifiedEvent prev = CURRENT.get();
        CURRENT.set(event);
        try {
            action.run();
        } finally {
            if (prev == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(prev);
            }
        }
    }

    static HapticUnifiedEvent current() {
        return CURRENT.get();
    }
}
