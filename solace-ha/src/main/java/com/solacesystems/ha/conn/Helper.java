package com.solacesystems.ha.conn;

import com.solacesystems.solclientj.core.handle.Handle;

public class Helper {

    public static void destroyHandle(Handle handle) {
        try {
            if (handle != null && handle.isBound()) {
                handle.destroy();
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
}
