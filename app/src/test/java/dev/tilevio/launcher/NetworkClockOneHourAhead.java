package dev.tilevio.launcher;

import android.os.SystemClock;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowPausedSystemClock;

/** Makes network time differ from system time, as it does after a manual time change. */
@Implements(SystemClock.class)
public class NetworkClockOneHourAhead extends ShadowPausedSystemClock {
    @Implementation
    protected static long currentNetworkTimeMillis() {
        return SystemClock.uptimeMillis() + 3_600_000;
    }
}
