package nachos.threads;

import com.sun.org.apache.bcel.internal.generic.ARRAYLENGTH;
import com.sun.org.apache.xml.internal.utils.ThreadControllerWrapper;
import nachos.machine.*;
import nachos.machine.Timer;

import javax.crypto.Mac;
import java.util.*;

/**
 * Uses the hardware timer to provide preemption, and to allow threads to sleep
 * until a certain time.
 */
public class Alarm {
    /**
     * Allocate a new Alarm. Set the machine's timer interrupt handler to this
     * alarm's callback.
     * <p>
     * <p><b>Note</b>: Nachos will not function correctly with more than one
     * alarm.
     */
    public Alarm() {
        Machine.timer().setInterruptHandler(new Runnable() {
            public void run() {
                timerInterrupt();
            }
        });

    }

    /**
     * The timer interrupt handler. This is called by the machine's timer
     * periodically (approximately every 500 clock ticks). Causes the current
     * thread to yield, forcing a context switch if there is another thread
     * that should be run.
     */
    public void timerInterrupt() {
        // modified by Crispher

        boolean intstatus = Machine.interrupt().disable();
        ListIterator<KThreadWrapper> iter = waitQueue.listIterator();
        while (iter.hasNext()) {
            KThreadWrapper t = iter.next();
            if (t.wakeTime < Machine.timer().getTime()) {
                t.kThread.ready();
            }
            iter.remove();
        }

        Machine.interrupt().restore(intstatus);
        // end Crispher

        KThread.currentThread().yield();
    }

    /**
     * Put the current thread to sleep for at least <i>x</i> ticks,
     * waking it up in the timer interrupt handler. The thread must be
     * woken up (placed in the scheduler ready set) during the first timer
     * interrupt where
     * <p>
     * <p><blockquote>
     * (current time) >= (WaitUntil called time)+(x)
     * </blockquote>
     *
     * @param    x    the minimum number of clock ticks to wait.
     * @see    nachos.machine.Timer#getTime()
     */
    public void waitUntil(long x) {
        /* commented out by Crispher
        // for now, cheat just to get something working (busy waiting is bad)
        long wakeTime = Machine.timer().getTime() + x;
        while (wakeTime > Machine.timer().getTime())
            KThread.yield();
        */
        // modified by Crispher
        lock.acquire();
        long wakeTime = Machine.timer().getTime() + x;
        waitQueue.add(new KThreadWrapper(KThread.currentThread(), wakeTime));
        lock.release();

        KThread.sleep();

        // end
    }

    // modified by Crispher
    class KThreadWrapper {
        public KThread kThread;
        public long wakeTime;
        public KThreadWrapper(KThread kThread, long wakeTime) {
            this.kThread = kThread;
            this.wakeTime = wakeTime;
        }
    }

    private LinkedList<KThreadWrapper> waitQueue = new LinkedList<>();
    Lock lock = new Lock();
    //end
}
