package nachos.threads;

import nachos.machine.*;

import java.awt.*;
import java.util.Random;
import java.util.TreeSet;
import java.util.Iterator;

/**
 * A scheduler that chooses threads using a lottery.
 * <p>
 * <p>
 * A lottery scheduler associates a number of tickets with each thread. When a
 * thread needs to be dequeued, a random lottery is held, among all the tickets
 * of all the threads waiting to be dequeued. The thread that holds the winning
 * ticket is chosen.
 * <p>
 * <p>
 * Note that a lottery scheduler must be able to handle a lot of tickets
 * (sometimes billions), so it is not acceptable to maintain state for every
 * ticket.
 * <p>
 * <p>
 * A lottery scheduler must partially solve the priority inversion problem; in
 * particular, tickets must be transferred through locks, and through joins.
 * Unlike a priority scheduler, these tickets add (as opposed to just taking
 * the maximum).
 */
public class LotteryScheduler extends Scheduler {
    /**
     * Allocate a new lottery scheduler.
     */
    public LotteryScheduler() {
    }

    /**
     * Allocate a new lottery thread queue.
     *
     * @param    transferPriority    <tt>true</tt> if this queue should
     * transfer tickets from waiting threads
     * to the owning thread.
     * @return a new lottery thread queue.
     */
    public ThreadQueue newThreadQueue(boolean transferPriority) {
        // implement me
        return new LPriorityQueue(transferPriority);

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

    public int getPriority(KThread thread) {
        Lib.assertTrue(Machine.interrupt().disabled());

        return getLThreadState(thread).getPriority();
    }


    public int getEffectivePriority(KThread thread) {
        Lib.assertTrue(Machine.interrupt().disabled());

        return getLThreadState(thread).getEffectivePriority();
    }


    public void setPriority(KThread thread, int priority) {
        Lib.assertTrue(Machine.interrupt().disabled());

        Lib.assertTrue(priority >= priorityMinimum
                && priority <= priorityMaximum);

        getLThreadState(thread).setPriority(priority);
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

    public static Random randomGen = new Random();

    /**
     * Return the scheduling state of the specified thread.
     *
     * @param thread
     *            the thread whose scheduling state to return.
     * @return the scheduling state of the specified thread.
     */
    protected LThreadState getLThreadState(KThread thread) {
        if (thread.schedulingState == null)
            thread.schedulingState = new LThreadState(thread);

        return (LThreadState) thread.schedulingState;
    }

    /**
     * A <tt>ThreadQueue</tt> that sorts threads by priority.
     */
    protected class LPriorityQueue extends ThreadQueue implements
            Comparable<LPriorityQueue> {
        LPriorityQueue(boolean transferPriority) {
            this.transferPriority = transferPriority;
        }

        public void waitForAccess(KThread thread) {
            Lib.assertTrue(Machine.interrupt().disabled());

            getLThreadState(thread).waitForAccess(this, enqueueTimeCounter++);
        }

        public void acquire(KThread thread) {
            Lib.assertTrue(Machine.interrupt().disabled());

            if (!transferPriority)
                return;

            getLThreadState(thread).acquire(this);
            occupyingThread = thread;
        }

        public KThread nextThread() {
            Lib.assertTrue(Machine.interrupt().disabled());

            //print();

            LThreadState nextThread = pickNextThread();

            if (occupyingThread != null) {
                getLThreadState(occupyingThread).release(this);
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
        protected LThreadState pickNextThread() {
            if (!waitingQueue.isEmpty()) {
                Lib.assertTrue(numTickets > 0);
                int lottery = randomGen.nextInt(numTickets);
                int sum = 0;
                for (LThreadState thread:
                        waitingQueue) {
                    if (sum <= lottery &&  lottery < sum + thread.getEffectivePriority()) {
                        return thread;
                    }
                    sum += thread.getEffectivePriority();
                }
                Lib.assertTrue(false);
                return waitingQueue.first();
            }
            return null;
        }

        public void print() {
            Lib.assertTrue(Machine.interrupt().disabled());

            for (Iterator<LThreadState> iterator = waitingQueue.iterator(); iterator
                    .hasNext();) {
                LThreadState state = iterator.next();
                System.out.print(state.getThread());
            }
            System.out.println("Total donating/tickets: " + donatingPriority + " " + numTickets);
            System.out.println();
        }

        public int getDonatingPriority() {
            return donatingPriority;
        }

        public int compareTo(LPriorityQueue queue) {
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
            boolean success = waitingQueue.remove(getLThreadState(thread));

            Lib.assertTrue(success);
        }

        public void updateEffectivePriority(KThread thread) {
            waitingQueue.add(getLThreadState(thread));

            updateDonatingPriority();
        }

        protected void updateDonatingPriority() {
            int newDonatingPriority = 0;
            int newNumTickets = 0;

            for (LThreadState lThreadState :
                    waitingQueue) {
                newNumTickets += lThreadState.getEffectivePriority();
            }

            if (transferPriority) {
                newDonatingPriority = newNumTickets;
            }
            else
                newDonatingPriority = 0;

            numTickets = newNumTickets;
            if (newDonatingPriority == donatingPriority)
                return;

            if (occupyingThread != null)
                getLThreadState(occupyingThread)
                        .prepareToUpdateDonatingPriority(this);

            donatingPriority = newDonatingPriority;

            if (occupyingThread != null)
                getLThreadState(occupyingThread).updateDonatingPriority(this);
        }

        /**
         * <tt>true</tt> if this queue should transfer priority from waiting
         * threads to the owning thread.
         */
        public boolean transferPriority;

        /** The threads waiting in this ThreadQueue. */
        protected TreeSet<LThreadState> waitingQueue = new TreeSet<LThreadState>();

        /** The thread occupying this ThreadQueue. */
        protected KThread occupyingThread = null;

        protected int donatingPriority = 0;
        protected int numTickets = 0;

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
    protected class LThreadState implements Comparable<LThreadState> {
        /**
         * Allocate a new <tt>LThreadState</tt> object and associate it with the
         * specified thread.
         *
         * @param thread
         *            the thread this state belongs to.
         */
        public LThreadState(KThread thread) {
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
        public void waitForAccess(LPriorityQueue waitQueue, long enqueueTime) {
            this.enqueueTime = enqueueTime;

            waitingFor = waitQueue;

            waitQueue.updateEffectivePriority(thread);
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
        public void acquire(LPriorityQueue waitQueue) {
            acquires.add(waitQueue);

            updateEffectivePriority();
        }

        /**
         * Called when <tt>waitQueue</tt> no longer be acquired by the
         * associated thread.
         *
         * @param waitQueue
         *            the queue
         */
        public void release(LPriorityQueue waitQueue) {
            acquires.remove(waitQueue);

            updateEffectivePriority();
        }

        public void ready() {
            Lib.assertTrue(waitingFor != null);

            waitingFor = null;
        }

        public int compareTo(LThreadState state) {

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

        /**
         * Remove <tt>waitQueue</tt> from <tt>acquires</tt> to prepare to update
         * <tt>donatingPriority</tt> of <tt>waitQueue</tt>.
         *
         * @param waitQueue
         */
        public void prepareToUpdateDonatingPriority(LPriorityQueue waitQueue) {
            boolean success = acquires.remove(waitQueue);

            Lib.assertTrue(success);
        }

        public void updateDonatingPriority(LPriorityQueue waitQueue) {
            acquires.add(waitQueue);

            updateEffectivePriority();
        }

        private void updateEffectivePriority() {
            int newEffectivePriority = priority;
            for (LPriorityQueue queue :
                    acquires) {
                newEffectivePriority += queue.donatingPriority;
            }
            if (newEffectivePriority == effectivePriority)
                return;

            if (waitingFor != null)
                waitingFor.prepareToUpdateEffectivePriority(thread);

            effectivePriority = newEffectivePriority;

            if (waitingFor != null)
                waitingFor.updateEffectivePriority(thread);
        }

        /** The thread with which this object is associated. */
        protected KThread thread;
        /** The priority of the associated thread. */
        protected int priority = priorityDefault;
        /** The effective priority of the associated thread. */
        protected int effectivePriority = priorityDefault;
        /** The ThreadQueue that the associated thread waiting for. */
        protected LPriorityQueue waitingFor = null;
        /** The TreeMap storing the number of donated priorities. */
        protected TreeSet<LPriorityQueue> acquires = new TreeSet<LPriorityQueue>();
        /**
         * The time when the thread begin to wait. That time is measured by
         * counting how many times <tt>LPriorityQueue.waitForAccess</tt> called
         * before.
         */
        protected long enqueueTime;
    }

    public void selfTest() {
        System.out.println("Lottery test");
        Machine.interrupt().disable();
        int N = 1000;
        KThread T[] = new KThread[N];
        for (int i = 0; i < N; i++) {
            T[i] = new KThread();
            T[i].setName("" + i);
        }
        LPriorityQueue q0 = new LPriorityQueue(true), q1 = new LPriorityQueue(true), q2 = new LPriorityQueue(false);

        for (int i = 0; i < N; i++) {
            q2.waitForAccess(T[i]);
        }
        for (int i = 0; i < N; i++) {
            setPriority(T[i], i + 1);
        }
        for (int i = 0; i < N; i++) {
            q2.nextThread();
        }
        System.out.println("Lottery test end");
    }
}
