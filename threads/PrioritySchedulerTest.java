package nachos.threads;

import nachos.machine.Machine;

/**
 * Created by Polo on 3/29/2016.
 */
public class PrioritySchedulerTest {
    private void priorityTest1() {
        Machine.interrupt().disable();
        KThread t0 = new KThread(), t1 = new KThread(), t2 = new KThread();
        t0.setName("t0");
        t1.setName("t1");
        t2.setName("t2");

        PriorityScheduler priorityScheduler = new PriorityScheduler();

        PriorityScheduler.ThreadState ts0 =
                priorityScheduler.getThreadState(t0);
        PriorityScheduler.ThreadState ts1 =
                priorityScheduler.getThreadState(t1);
        PriorityScheduler.ThreadState ts2 =
                priorityScheduler.getThreadState(t2);

        PriorityScheduler.PriorityQueue r0 = (PriorityScheduler.PriorityQueue)
                priorityScheduler.newThreadQueue(true);
        PriorityScheduler.PriorityQueue r1 = (PriorityScheduler.PriorityQueue)
                priorityScheduler.newThreadQueue(true);
        r0.name = "r0"; r1.name = "r1";

        r0.waitForAccess(t0);
        priorityScheduler.setPriority(t0, 3);
        r0.waitForAccess(t1);
        priorityScheduler.setPriority(t1, 4);
        KThread t = r0.nextThread();
        priorityScheduler.getThreadState(t).print();
        r1.waitForAccess(t);
        r1.print();
        priorityScheduler.setPriority(t0, 5);
        r0.print();
        r1.print();
        ts0.print();
        ts1.print();

        PriorityScheduler.debug("");
        priorityScheduler.setPriority(t1, 2);
        r0.print();
        r1.print();
        ts0.print();
        ts1.print();

        r1.nextThread();
        PriorityScheduler.debug("");
        r0.print();
        r1.print();
        ts0.print();
        ts1.print();

        r0.nextThread();
        PriorityScheduler.debug("");
        r0.print();
        r1.print();
        ts0.print();
        ts1.print();
    }

    public void RunAllTest() {
        priorityTest1();
    }

}
