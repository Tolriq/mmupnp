/*
 * Copyright(C) 2016 大前良介(OHMAE Ryosuke)
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/MIT
 */

package net.mm2d.upnp;

import net.mm2d.util.Log;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import javax.annotation.Nonnull;

/**
 * @author <a href="mailto:ryo@mm2d.net">大前良介(OHMAE Ryosuke)</a>
 */
class SubscribeKeeper extends Thread {
    private static final String TAG = "SubscribeKeeper";
    private static final long MARGIN_TIME = 10000;
    private static final long MIN_INTERVAL = 1000;
    private final ControlPoint mControlPoint;
    private volatile boolean mShutdownRequest = false;
    private final List<Service> mServiceList;
    private final Comparator<Service> mComparator = new Comparator<Service>() {
        @Override
        public int compare(Service s1, Service s2) {
            return (int) (getRenewTime(s1) - getRenewTime(s2));
        }
    };

    private long getRenewTime(@Nonnull Service service) {
        long timeout = service.getSubscriptionTimeout();
        if (timeout > MARGIN_TIME) {
            timeout -= MARGIN_TIME;
        } else {
            timeout = timeout * 9 / 10;
        }
        return service.getSubscriptionStart() + timeout;
    }

    public SubscribeKeeper(@Nonnull ControlPoint controlPoint) {
        super(TAG);
        mControlPoint = controlPoint;
        mServiceList = new ArrayList<>();
    }

    public void shutdownRequest() {
        mShutdownRequest = true;
        interrupt();
    }

    public synchronized void update() {
        Collections.sort(mServiceList, mComparator);
    }

    public synchronized void add(@Nonnull Service service) {
        mServiceList.add(service);
        Collections.sort(mServiceList, mComparator);
        notifyAll();
    }

    public synchronized void remove(@Nonnull Service service) {
        mServiceList.remove(service);
    }

    public synchronized void clear() {
        mServiceList.clear();
    }

    @Override
    public void run() {
        try {
            while (!mShutdownRequest) {
                final List<Service> work;
                synchronized (this) {
                    while (mServiceList.size() == 0) {
                        wait();
                    }
                    work = new ArrayList<>(mServiceList);
                }
                final long now = System.currentTimeMillis();
                for (final Service service : work) {
                    if (getRenewTime(service) < now) {
                        try {
                            service.renewSubscribe(false);
                        } catch (final IOException e) {
                            Log.w(TAG, e);
                        }
                    } else {
                        break;
                    }
                }
                mControlPoint.removeExpiredSubscribeService();
                synchronized (this) {
                    Collections.sort(mServiceList, mComparator);
                    if (mServiceList.size() != 0) {
                        final Service service = mServiceList.get(0);
                        long sleep = getRenewTime(service) - System.currentTimeMillis();
                        if (sleep < MIN_INTERVAL) {
                            sleep = MIN_INTERVAL;
                        }
                        wait(sleep);
                    }
                }
            }
        } catch (final InterruptedException ignored) {
        }
    }
}
