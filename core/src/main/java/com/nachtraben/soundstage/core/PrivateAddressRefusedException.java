package com.nachtraben.soundstage.core;

import java.net.UnknownHostException;

public final class PrivateAddressRefusedException extends UnknownHostException {

    private final boolean platform;

    public PrivateAddressRefusedException(String message, boolean platform) {
        super(message);
        this.platform = platform;
    }

    public boolean platform() {
        return platform;
    }
}
