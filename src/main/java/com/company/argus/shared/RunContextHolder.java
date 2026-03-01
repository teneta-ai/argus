package com.company.argus.shared;

/**
 * Thread-scoped holder for the current agent run context.
 * The agent module sets this before dispatching to an agent;
 * tool and security modules read it to make authorization decisions.
 */
public final class RunContextHolder {

    private static final ThreadLocal<RunContext> CURRENT = new ThreadLocal<>();

    private RunContextHolder() {}

    public static RunContext current() {
        RunContext ctx = CURRENT.get();
        if (ctx == null) {
            throw new IllegalStateException("No RunContext set on current thread");
        }
        return ctx;
    }

    public static void set(RunContext context) {
        CURRENT.set(context);
    }

    public static void clear() {
        CURRENT.remove();
    }
}
