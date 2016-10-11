package eu.inn.gc;

import java.util.EventListener;

public interface GCListener extends EventListener {

    void handleNotification(GCNotification gcNotification);
}
