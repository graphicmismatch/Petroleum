package Petroleum.Collab;

import java.io.IOException;

public final class UntrustedServerException extends IOException {
    private final String fingerprint;
    private final boolean changed;

    UntrustedServerException(String fingerprint, boolean changed, Throwable cause) {
        super(changed ? "the server's certificate changed since you last trusted it"
                : "the server's certificate is not trusted", cause);
        this.fingerprint = fingerprint;
        this.changed = changed;
    }

    public String fingerprint() {
        return fingerprint;
    }

    public boolean changed() {
        return changed;
    }
}
