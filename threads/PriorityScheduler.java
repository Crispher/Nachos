package nachos.threads;

import nachos.machine.*;

import java.util.*;

/**
 * A scheduler that chooses threads based on their priorities.
 * <p>
 * <p>
 * A priority scheduler associates a priority with each thread. The next thread
 * to be dequeued is always a thread with priority no less than any other
 * waiting thread's priority. Like a round-robin scheduler, the thread that is
 * dequeued is, among all the threads of the same (highest) priority, the
 * thread that has been waiting longest.
 * <p>
 * <p>
 * Essentially, a priority scheduler gives access in a round-robin fassion to
 * all the highest-priority threads, and ignores all other threads. This has
 * the potential to
 * starve a thread if there's always a thread waiting with higher priority.
 * <p>
 * <p>
 * A priority scheduler must partially solve the priority inversion problem; in
 * particular, priority must be donated through locks, and through joins.
 */
public class PriorityScheduler extends Scheduler {

    /**
     * For debug and test purpose only
     *
     */
    public static void debug(String s) {
        System.out.println(s);
    }

    public void selfTest() {
        Machine.interrupt().disable();
        KThread t0 = new KThread(), t1 = new KThread(), t2 = new KThread();
        t0.setName("t0"); t1.setName("t1"); t2.setName("t2");
        ThreadState ts0 = new ThreadState(t0), ts1 = new ThreadState(t1), ts2 = new ThreadState(t2);
        ts0.enqueueTime = 12;
        ts1.enqueueTime = 13;
        ts2.enqueueTime = 12;
        ts0.effectivePriority = 3;
        ts1.effectivePriority = 4;
        ts2.effectivePriority = 4;
        TreeSet<ThreadState> treeSet = new TreeSet<>(new ThreadComparator());
        treeSet.add(ts0);
        treeSet.add(ts1);
        treeSet.add(ts2);
        treeSet.remove(treeSet.first());

        PriorityQueue pq = (PriorityQueue) newThreadQueue(true);
        pq.waitForAccess(t0);
        pq.waitForAccess(t1);
        KThread t = pq.nextThread();
        debug("" + (t == t0) + (pq.currentHolder == t0.schedulingState));
        KThread tt = pq.nextThread();
        debug("" + (tt == t1) + (pq.currentHolder == t1.schedulingState));
    }


    /**
     * Allocate a new priority scheduler.
     */
    public PriorityScheduler() {
    }

    /**
     * Allocate a new priority thread queue.
     *
     * @param    transferPriority    <tt>true</tt> if this queue should
     * transfer priority from waiting threads
     * to the owning thread.
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

        Lib.assertTrue(priority >= priorityMinimum &&
                priority <= priorityMaximum);

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
    public static final int priorityMinimum = 0;
    /**
     * The maximum priority that a thread can have. Do not change this value.
     */
    public static final int priorityMaximum = 7;

    /**
     * Return the scheduling state of the specified thread.
     *
     * @param    thread    the thread whose scheduling state to return.
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
    protected class PriorityQueue extends ThreadQueue {
        PriorityQueue(boolean transferPriority) {
            this.transferPriority = transferPriority;
        }

        public void waitForAccess(KThread thread) {
            Lib.assertTrue(Machine.interrupt().disabled());
            getThreadState(thread).waitForAccess(this);
        }

        public void acquire(KThread thread) {
            Lib.assertTrue(Machine.interrupt().disabled());
            getThreadState(thread).acquire(this);
        }

        public KThread nextThread() {
            Lib.assertTrue(Machine.interrupt().disabled());
            // implement me
            // Crispher, add members to specify which "threadState"s are waiting on this queue
            // choose the one with highest effective priority, should call acquire();
            // the queue, as a resource should have its priority as member, update when
            // acquire() or waitForAccess() is called
            debug("c: ");
            if (threadTreeSet.isEmpty()) {
                debug("d: ");
                return null;
            }

            ThreadState t = threadTreeSet.first();
            threadTreeSet.remove(t);
            updatePriority();

            // t no longer wait for this since he already acquired this
            Lib.assertTrue(t.waitingResource == this);
            t.waitingResource = null;

            t.acquire(this);
            return t.thread;
        }

        /**
         * Return the next thread that <tt>nextThread()</tt> would return,
         * without modifying the state of this queue.
         *
         * @return the next thread that <tt>nextThread()</tt> would
         * return.
         */
        protected ThreadState pickNextThread() {
            // implement me
            // Crispher useful to decide the new priority of this queue.
            if (threadTreeSet.isEmpty()) {
                return null;
            } else {
                return threadTreeSet.first();
            }
        }

        public void print() {
            Lib.assertTrue(Machine.interrupt().disabled());
            // implement me (if you want)
        }

        /**
         * <tt>true</tt> if this queue should transfer priority from waiting
         * threads to the owning thread.
         */
        public boolean transferPriority;

        // modified by Crispher
        // the priority of the queue donated by its waiters

        protected int priority = -1;
        protected TreeSet<ThreadState> threadTreeSet = new TreeSet<>(new ThreadComparator());
        protected ThreadState currentHolder = null;

        /**
         * update the priority of the queue caused by the effective priority change in
         * thread, also update the threads effective priority.
         */
        protected void updatePriority(ThreadState thread, int priority) {
            debug("f: " + threadTreeSet.size());
            Lib.assertTrue(threadTreeSet.contains(thread));
            // either a raise or a fall
            if (thread.effectivePriority != priority) {
                threadTreeSet.remove(thread);
                thread.effectivePriority = priority;
                threadTreeSet.add(thread);
            }
            updatePriority();
            debug("g: " + threadTreeSet.size());
        }

        protected void updatePriority() {
            if (transferPriority && !threadTreeSet.isEmpty()) {
                if (this.priority != pickNextThread().effectivePriority) {
                    this.priority = pickNextThread().effectivePriority;
                    if (currentHolder != null) {
                        currentHolder.updateEffectivePriority(this);
                    }
                }
            } else {
                priority = 0;
            }
        }
        // end
    }

    // modified by Crispher

    /**
     * first compare effective priority, then enqueue time.
     * Tested Code.
     */

    protected class ThreadComparator implements Comparator<ThreadState> {
        @Override
        public int compare(ThreadState t0, ThreadState t1) {
            if (t0.effectivePriority > t1.effectivePriority) {
                return -1;
            } else if (t0.effectivePriority == t1.effectivePriority) {
                if (t0.enqueueTime < t1.enqueueTime) {
                    return -1;
                } else if (t0.enqueueTime == t1.enqueueTime) {
                    return t0.thread.compareTo(t1.thread);
                } else {
                    return 1;
                }
            } else {
                return 1;
            }
        }
    }

    // end

    /**
     * The scheduling state of a thread. This should include the thread's
     * priority, its effective priority, any objects it owns, and the queue
     * it's waiting for, if any.
     *
     * @see    nachos.threads.KThread#schedulingState
     */
    protected class ThreadState {
        /**
         * Allocate a new <tt>ThreadState</tt> object and associate it with the
         * specified thread.
         *
         * @param    thread    the thread this state belongs to.
         */
        public ThreadState(KThread thread) {
            this.thread = thread;

            setPriority(priorityDefault);

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
            // implement me
            // Crispher
            return effectivePriority;
        }

        /**
         * Set the priority of the associated thread to the specified value.
         *
         * @param    priority    the new priority.
         */
        public void setPriority(int priority) {
            // modified by Crispher. assert only called on initiation.
            Lib.assertTrue(effectivePriority < 0);
            if (effectivePriority < 0) {
                effectivePriority = priority;
            }

            if (this.priority == priority)
                return;

            this.priority = priority;

            // implement me
            // Modified by Crispher

        }

        /**
         * Update effective priority on change of a holding resource;
         */
        public void updateEffectivePriority(PriorityQueue priorityQueue) {
            Lib.assertTrue(priorityQueue.transferPriority);
            int newPriority = -1;
            // a raise, only update if necessary
            if (priorityQueue.priority > effectivePriority) {
                newPriority = priorityQueue.priority;
            }
            // a fall, recompute the max
            if (priorityQueue.priority < effectivePriority) {
                for (PriorityQueue holdingResource : holdingResources) {
                    if (holdingResource.priority > newPriority) {
                        newPriority = holdingResource.priority;
                    }
                }
            }

            if (newPriority >= 0) {
                // there is a need to update effective priority
                if (waitingResource == null) {
                    effectivePriority = newPriority;
                } else {
                    // let the waitqueue update my effective priority, as well as his own
                    waitingResource.updatePriority(this, newPriority);
                }
            }
        }

        /**
         * update on release of a holding resource
         */
        public void updateEffectivePriority() {
            int newPriority = -1;
            for (PriorityQueue holdingResource : holdingResources) {
                if (holdingResource.priority > newPriority) {
                    newPriority = holdingResource.priority;
                }
            }
            Lib.assertTrue(newPriority <= effectivePriority);
            if (newPriority > 0 && this.effectivePriority != newPriority) {
                // in case the set is non-empty and a change is necessary
                if (waitingResource == null) {
                    effectivePriority = newPriority;
                } else {
                    waitingResource.updatePriority(this, newPriority);
                }
            }
        }

        /**
         * Called when <tt>waitForAccess(thread)</tt> (where <tt>thread</tt> is
         * the associated thread) is invoked on the specified priority queue.
         * The associated thread is therefore waiting for access to the
         * resource guarded by <tt>waitQueue</tt>. This method is only called
         * if the associated thread cannot immediately obtain access.
         *
         * @param    waitQueue    the queue that the associated thread is
         * now waiting on.
         * @see    nachos.threads.ThreadQueue#waitForAccess
         */
        public void waitForAccess(PriorityQueue waitQueue) {

            // implement me
            // Crispher, should update the resource holder's e priority
            debug("i: " + thread.getName());
            Lib.assertTrue(!waitQueue.threadTreeSet.contains(this));

            enqueueTime = Machine.timer().getTime();

            Lib.assertTrue(waitingResource == null);
            waitingResource = waitQueue;
            debug("h: " + waitQueue.threadTreeSet.size());
            waitQueue.threadTreeSet.add(this);
            debug("e: " + waitQueue.threadTreeSet.size());
            waitQueue.updatePriority(this, this.effectivePriority);
            // done
        }

        /**
         * Called when the associated thread has acquired access to whatever is
         * guarded by <tt>waitQueue</tt>. This can occur either as a result of
         * <tt>acquire(thread)</tt> being invoked on <tt>waitQueue</tt> (where
         * <tt>thread</tt> is the associated thread), or as a result of
         * <tt>nextThread()</tt> being invoked on <tt>waitQueue</tt>.
         *
         * @see    nachos.threads.ThreadQueue#acquire
         * @see    nachos.threads.ThreadQueue#nextThread
         */
        public void acquire(PriorityQueue waitQueue) {
            // implement me
            // Crispher, update my e priority if waitQueue.transfer is on
            debug("a: ");
            if (waitQueue.currentHolder != null) {
                debug("b: ");
                waitQueue.currentHolder.release(waitQueue);
            }
            waitQueue.currentHolder = this;
            holdingResources.add(waitQueue);
            updateEffectivePriority(waitQueue);
        }

        /**
         * release the queue that is currently held by this.
         * notify my waiting queue (if any) to update my effective priority
         */

        public void release(PriorityQueue holdingResource) {
            Lib.assertTrue(holdingResources.contains(holdingResource));
            holdingResources.remove(holdingResource);
            updateEffectivePriority();
        }

        /**
         * The thread with which this object is associated.
         */
        protected KThread thread;
        /**
         * The priority of the associated thread.
         */
        protected int priority;

        // modified by Crispher
        protected int effectivePriority = -1;
        /**
         * the time this thread is enqueued.
         */
        protected long enqueueTime = -1;
        protected PriorityQueue waitingResource = null;
        protected LinkedList<PriorityQueue> holdingResources = new LinkedList<>();
        // end
    }
}
