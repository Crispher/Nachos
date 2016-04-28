//yhdxt`oi`offt`of{inofinofmhphofx`ofxholhofuh`ov`ofphorih
//PART OF THE NACHOS. DON'T CHANGE CODE OF THIS LINE
package nachos.threads;

import nachos.machine.*;

import java.util.Random;
import java.util.TreeSet;
import java.util.HashSet;
import java.util.Iterator;

/**
 * A scheduler that chooses threads based on their priorities.
 *
 * <p>
 * A priority scheduler associates a priority with each thread. The next thread
 * to be dequeued is always a thread with priority no less than any other
 * waiting thread's priority. Like a round-robin scheduler, the thread that is
 * dequeued is, among all the threads of the same (highest) priority, the thread
 * that has been waiting longest.
 *
 * <p>
 * Essentially, a priority scheduler gives access in a round-robin fassion to
 * all the highest-priority threads, and ignores all other threads. This has the
 * potential to starve a thread if there's always a thread waiting with higher
 * priority.
 *
 * <p>
 * A priority scheduler must partially solve the priority inversion problem; in
 * particular, priority must be donated through locks, and through joins.
 */
public class LotteryScheduler extends Scheduler {
    /**
     * Allocate a new priority scheduler.
     */
    public LotteryScheduler() {
    }

    /**
     * Allocate a new priority thread queue.
     *
     * @param transferPriority
     *            <tt>true</tt> if this queue should transfer priority from
     *            waiting threads to the owning thread.
     * @return a new priority thread queue.
     */
    public ThreadQueue newThreadQueue(boolean transferPriority) {
        return new PriorityQueue(transferPriority);
    }

    public int getPriority(KThread thread) {
        Lib.assertTrue(Machine.interrupt().disabled());

        return getThreadState(thread).getPriority();
    }

    public int getEffectivePriority(KThread thread) {
        Lib.assertTrue(Machine.interrupt().disabled());

        return getThreadState(thread).getEffectivePriority();
    }

    public void setPriority(KThread thread, int priority) {
        Lib.assertTrue(Machine.interrupt().disabled());

        Lib.assertTrue(priority >= priorityMinimum
                && priority <= priorityMaximum);

        getThreadState(thread).setPriority(priority);
    }

    public boolean increasePriority() {
        boolean intStatus = Machine.interrupt().disable();

        KThread thread = KThread.currentThread();

        int priority = getPriority(thread);
        if (priority == priorityMaximum)
            return false;

        setPriority(thread, priority + 1);

        Machine.interrupt().restore(intStatus);
        return true;
    }

    public boolean decreasePriority() {
        boolean intStatus = Machine.interrupt().disable();

        KThread thread = KThread.currentThread();

        int priority = getPriority(thread);
        if (priority == priorityMinimum)
            return false;

        setPriority(thread, priority - 1);

        Machine.interrupt().restore(intStatus);
        return true;
    }

    /**
     * The default priority for a new thread. Do not change this value.
     */
    public static final int priorityDefault = 1;
    /**
     * The minimum priority that a thread can have. Do not change this value.
     */
    public static final int priorityMinimum = 1;
    /**
     * The maximum priority that a thread can have. Do not change this value.
     */
    public static final int priorityMaximum = Integer.MAX_VALUE;

    public static final int MAX_UPDATES = 20;

    /**
     * Return the scheduling state of the specified thread.
     *
     * @param thread
     *            the thread whose scheduling state to return.
     * @return the scheduling state of the specified thread.
     */
    protected ThreadState getThreadState(KThread thread) {
        if (thread.schedulingState == null)
            thread.schedulingState = new ThreadState(thread);

        return (ThreadState) thread.schedulingState;
    }

    /**
     * A <tt>ThreadQueue</tt> that sorts threads by priority.
     */
    protected class PriorityQueue extends ThreadQueue implements
            Comparable<PriorityQueue> {
        PriorityQueue(boolean transferPriority) {
            this.transferPriority = transferPriority;
        }

        public void waitForAccess(KThread thread) {
            Lib.assertTrue(Machine.interrupt().disabled());

            getThreadState(thread).waitForAccess(this, enqueueTimeCounter++);
        }

        public void acquire(KThread thread) {
            Lib.assertTrue(Machine.interrupt().disabled());

            if (!transferPriority)
                return;

            getThreadState(thread).acquire(this);
            occupyingThread = thread;
        }

        public KThread nextThread() {
            Lib.assertTrue(Machine.interrupt().disabled());

            //print();

            ThreadState nextThread = pickNextThread();

            if (occupyingThread != null) {
                getThreadState(occupyingThread).release(this);
                occupyingThread = null;
            }

            if (nextThread == null)
                return null;

            waitingQueue.remove(nextThread);
            nextThread.ready();

            updateDonatingPriority();

            acquire(nextThread.getThread());

            return nextThread.getThread();
        }

        /**
         * Return the next thread that <tt>nextThread()</tt> would return,
         * without modifying the state of this queue.
         *
         * @return the next thread that <tt>nextThread()</tt> would return.
         */
        protected ThreadState pickNextThread() {

            if (!waitingQueue.isEmpty()) {

                Lib.assertTrue(numTickets > 0);
                int lottery = randomGen.nextInt(numTickets);
                int sum = 0;
                for (ThreadState thread:
                        waitingQueue) {
                    if (sum <= lottery &&  lottery < sum + thread.getEffectivePriority()) {
                        return thread;
                    }
                    sum += thread.getEffectivePriority();
                }
                Lib.assertNotReached();
            }
            return null;
        }

        public void print() {
            Lib.assertTrue(Machine.interrupt().disabled());

            for (Iterator<ThreadState> iterator = waitingQueue.iterator(); iterator
                    .hasNext();) {
                ThreadState state = iterator.next();
                System.out.print(state.getThread());
            }
            System.out.println();
        }

        public int getDonatingPriority() {
            return donatingPriority;
        }

        public int compareTo(PriorityQueue queue) {
            if (donatingPriority > queue.donatingPriority)
                return -1;
            if (donatingPriority < queue.donatingPriority)
                return 1;

            if (id < queue.id)
                return -1;
            if (id > queue.id)
                return 1;

            return 0;
        }

        public void prepareToUpdateEffectivePriority(KThread thread) {
            boolean success = waitingQueue.remove(getThreadState(thread));

            Lib.assertTrue(success);
        }

        public void updateEffectivePriority(KThread thread) {
            waitingQueue.add(getThreadState(thread));

            updateDonatingPriority();
        }

        protected void updateDonatingPriority() {
            int newDonatingPriority = 0, newNumTickets = 0;

            for (ThreadState ts : waitingQueue) {
                newNumTickets += ts.getEffectivePriority();
            }
            if (transferPriority) {
                newDonatingPriority = newNumTickets;
            }
            numTickets = newNumTickets;
            if (visited == 0) {
                visited = MAX_UPDATES;
                return;
            }
            visited--;
            if (newDonatingPriority == donatingPriority) {
                visited = MAX_UPDATES;
                return;
            }

            if (occupyingThread != null) {
                boolean success = getThreadState(occupyingThread).acquires.remove(this);
                Lib.assertTrue(success);
                donatingPriority = newDonatingPriority;
                getThreadState(occupyingThread).acquires.add(this);
                getThreadState(occupyingThread).updateEffectivePriority();
            } else {
                donatingPriority = newDonatingPriority;
            }
            visited = MAX_UPDATES;
        }

        /**
         * <tt>true</tt> if this queue should transfer priority from waiting
         * threads to the owning thread.
         */
        public boolean transferPriority;

        /** The threads waiting in this ThreadQueue. */
        protected TreeSet<ThreadState> waitingQueue = new TreeSet<ThreadState>();

        /** The thread occupying this ThreadQueue. */
        protected KThread occupyingThread = null;

        protected int donatingPriority = 0;
        protected int numTickets = 0;
        private Random randomGen = new Random();
        private int visited = MAX_UPDATES;
        /**
         * The number that <tt>waitForAccess</tt> has been called. Used know the
         * time when each thread enqueue.
         */
        protected long enqueueTimeCounter = 0;

        protected int id = numPriorityQueueCreated++;
    }

    protected static int numPriorityQueueCreated = 0;

    /**
     * The scheduling state of a thread. This should include the thread's
     * priority, its effective priority, any objects it owns, and the queue it's
     * waiting for, if any.
     *
     * @see nachos.threads.KThread#schedulingState
     */
    protected class ThreadState implements Comparable<ThreadState> {
        /**
         * Allocate a new <tt>LThreadState</tt> object and associate it with the
         * specified thread.
         *
         * @param thread
         *            the thread this state belongs to.
         */
        public ThreadState(KThread thread) {
            this.thread = thread;
        }

        public KThread getThread() {
            return thread;
        }

        /**
         * Return the priority of the associated thread.
         *
         * @return the priority of the associated thread.
         */
        public int getPriority() {
            return priority;
        }

        /**
         * Return the effective priority of the associated thread.
         *
         * @return the effective priority of the associated thread.
         */
        public int getEffectivePriority() {
            return effectivePriority;
        }

        /**
         * Return the time when the associated thread begin to wait.
         */
        public long getEnqueueTime() {
            return enqueueTime;
        }

        /**
         * Set the priority of the associated thread to the specified value.
         *
         * @param priority
         *            the new priority.
         */
        public void setPriority(int priority) {
            if (this.priority == priority)
                return;

            this.priority = priority;
            updateEffectivePriority();
        }

        /**
         * Called when <tt>waitForAccess(thread)</tt> (where <tt>thread</tt> is
         * the associated thread) is invoked on the specified priority queue.
         * The associated thread is therefore waiting for access to the resource
         * guarded by <tt>waitQueue</tt>. This method is only called if the
         * associated thread cannot immediately obtain access.
         *
         * @param waitQueue
         *            the queue that the associated thread is now waiting on.
         *
         * @param enqueueTime
         *            the time when the thread begin to wait.
         *
         * @see nachos.threads.ThreadQueue#waitForAccess
         */
        public void waitForAccess(PriorityQueue waitQueue, long enqueueTime) {
            this.enqueueTime = enqueueTime;

            waitingFor = waitQueue;

//            waitQueue.updateEffectivePriority(thread);

            waitQueue.waitingQueue.add(getThreadState(thread));
            waitQueue.updateDonatingPriority();

        }

        /**
         * Called when the associated thread has acquired access to whatever is
         * guarded by <tt>waitQueue</tt>. This can occur either as a result of
         * <tt>acquire(thread)</tt> being invoked on <tt>waitQueue</tt> (where
         * <tt>thread</tt> is the associated thread), or as a result of
         * <tt>nextThread()</tt> being invoked on <tt>waitQueue</tt>.
         *
         * @see nachos.threads.ThreadQueue#acquire
         * @see nachos.threads.ThreadQueue#nextThread
         */
        public void acquire(PriorityQueue waitQueue) {
            acquires.add(waitQueue);
            waitQueue.occupyingThread = thread;
            updateEffectivePriority();
        }

        /**
         * Called when <tt>waitQueue</tt> no longer be acquired by the
         * associated thread.
         *
         * @param waitQueue
         *            the queue
         */
        public void release(PriorityQueue waitQueue) {
            acquires.remove(waitQueue);
            waitQueue.occupyingThread = null;
            updateEffectivePriority();
        }

        public void ready() {
            Lib.assertTrue(waitingFor != null);

            waitingFor = null;
        }

        public int compareTo(ThreadState state) {

            if (effectivePriority > state.effectivePriority)
                return -1;
            if (effectivePriority < state.effectivePriority)
                return 1;

            if (enqueueTime < state.enqueueTime)
                return -1;
            if (enqueueTime > state.enqueueTime)
                return 1;

            return thread.compareTo(state.thread);
        }


        private void updateEffectivePriority() {
            if (visited == 0) {
                visited = MAX_UPDATES;
                return;
            }
            visited--;
            int newEffectivePriority = priority;

            for (PriorityQueue queue : acquires) {
                newEffectivePriority += queue.getDonatingPriority();
            }

            if (newEffectivePriority == effectivePriority) {
                visited = MAX_UPDATES;
                return;
            }

            if (waitingFor != null) {
                waitingFor.prepareToUpdateEffectivePriority(thread);
            }

            effectivePriority = newEffectivePriority;

            if (waitingFor != null) {
                waitingFor.updateEffectivePriority(thread);
            }
            visited = MAX_UPDATES;
        }

        /** The thread with which this object is associated. */
        protected KThread thread;
        /** The priority of the associated thread. */
        protected int priority = priorityDefault;
        /** The effective priority of the associated thread. */
        protected int effectivePriority = priorityDefault;
        /** The ThreadQueue that the associated thread waiting for. */
        private PriorityQueue waitingFor = null;
        /** The TreeMap storing the number of donated priorities. */
        private TreeSet<PriorityQueue> acquires = new TreeSet<PriorityQueue>();
        /**
         * The time when the thread begin to wait. That time is measured by
         * counting how many times <tt>LPriorityQueue.waitForAccess</tt> called
         * before.
         */
        protected long enqueueTime;
        private int visited = MAX_UPDATES;
    }

    public void selfTest() {
        Machine.interrupt().disable();
        int N = 100;
        KThread T[] = new KThread[N];
        for (int i = 0; i < N; i++) {
            T[i] = new KThread(new Runnable() {
                @Override
                public void run() {
                    return;
                }
            });
        }

        PriorityQueue q1 = new PriorityQueue(true), q2 = new PriorityQueue(true), q3 = new PriorityQueue(true);
        q1.acquire(T[1]);
        q2.acquire(T[2]);
        q3.acquire(T[3]);
        q1.waitForAccess(T[2]);
        q2.waitForAccess(T[3]);
        q3.waitForAccess(T[1]);

        setPriority(T[1], 4);
        setPriority(T[2], 5);
        setPriority(T[3], 6);

        q1.waitForAccess(T[0]);
    }
}
