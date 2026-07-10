package com.liteworkflow.common.security.user;

import com.liteworkflow.common.core.error.BizException;
import com.liteworkflow.common.core.error.CommonErrorCode;
import java.util.Optional;

/** Imperative request context. Reactive code must use Reactor Context instead. */
public final class UserContext {

    private static final ThreadLocal<CurrentUser> CURRENT = new ThreadLocal<>();

    private UserContext() {
    }

    public static Optional<CurrentUser> current() {
        return Optional.ofNullable(CURRENT.get());
    }

    public static CurrentUser requireCurrent() {
        return current().orElseThrow(() -> new BizException(CommonErrorCode.UNAUTHORIZED));
    }

    public static void set(CurrentUser user) {
        if (user == null) {
            throw new IllegalArgumentException("user must not be null");
        }
        CURRENT.set(user);
    }

    public static void clear() {
        CURRENT.remove();
    }

    public static Scope withUser(CurrentUser user) {
        CurrentUser previous = CURRENT.get();
        set(user);
        return new Scope(previous);
    }

    public static final class Scope implements AutoCloseable {

        private final CurrentUser previous;
        private boolean closed;

        private Scope(CurrentUser previous) {
            this.previous = previous;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
            closed = true;
        }
    }
}
