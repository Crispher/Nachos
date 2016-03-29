package nachos.threads;

import nachos.machine.Machine;

import java.util.ArrayList;
import java.util.Date;
import java.util.Random;

public class KThreadTest {

    private static ArrayList<KThread> joinTestHelper() {
        ArrayList<KThread> testThreads = new ArrayList<>();


        final KThread t0 = new KThread(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < 5; i++) {
                    System.out.println("Joined Thread looping " + i);
                    KThread.yield();
                }
            }
        });

        KThread t1 = new KThread(new Runnable() {
            @Override
            public void run() {
                System.out.println("Before join.");
                t0.join();
                System.out.println("After join, before halt");
            }
        });

        KThread t2 = new KThread(new Runnable() {
            @Override
            public void run() {
                System.out.println("t2 begins.");
                for (int i = 0; i < 100; i++)
                    KThread.yield();
                System.out.println("t2 ends.");
            }
        });

        testThreads.add(t0);
        testThreads.add(t1);
        testThreads.add(t2);

        return testThreads;
    }

    private static ArrayList<KThread> conditionTestHelper() {
        return null;
    }

    private void joinTest1() {
        System.out.println("[Test 1] begins.");
        ArrayList<KThread> threads = joinTestHelper();
        threads.get(0).fork();
        threads.get(1).fork();
        threads.get(1).join();

        threads.get(2).fork();
        threads.get(2).join();
        System.out.println("[Test 1] ends.");
    }

    private void joinTest2() {
        System.out.println("[Test 2] begins.");
        ArrayList<KThread> threads = joinTestHelper();
        threads.get(2).fork();

        threads.get(0).fork();
        threads.get(0).join();

        threads.get(1).fork();
        threads.get(1).join();

        threads.get(2).join();
        System.out.println("[Test 2] ends.");
    }

    private void conditionTest1() {
        System.out.println("[Condition][Test 1] begins.");
        final Lock conditionLock = new Lock();
        final Condition condition = new Condition(conditionLock);
        final KThread t0 = new KThread(new Runnable() {
            @Override
            public void run() {
                System.out.println("t0 begins.");
                conditionLock.acquire();
                System.out.println("t0 gets lock, and is going to sleep");
                condition.sleep();
                System.out.println("t0 wakes and wake condition");
                condition.wake();
                conditionLock.release();
                System.out.println("t0 releases lock, ends.");
            }
        });

        final KThread t1 = new KThread(new Runnable() {
            @Override
            public void run() {
                System.out.println("t1 begins.");
                conditionLock.acquire();
                System.out.println("t1 gets lock, and is going to sleep");
                condition.sleep();
                System.out.println("t1 wakes");
                condition.wake();
                conditionLock.release();
                System.out.println("t1 releases lock, ends.");
            }
        });

        final KThread t2 = new KThread(new Runnable() {
            @Override
            public void run() {
                System.out.println("t2 begins.");
                conditionLock.acquire();
                System.out.println("t2 gets lock, and is going to wake " +
                        "condition");
                condition.wake();
                conditionLock.release();
                System.out.println("t2 releases lock, ends.");
            }
        });

        t0.fork();
        t1.fork();
        t2.fork();
        t0.join();
        t1.join();
        t2.join();
        System.out.println("[Condition][Test 1] ends.");
    }

    private void condition2Test1() {
        System.out.println("[Condition2][Test 1] begins.");
        final Lock conditionLock = new Lock();
        final Condition2 condition = new Condition2(conditionLock);
        final KThread t0 = new KThread(new Runnable() {
            @Override
            public void run() {
                System.out.println("t0 begins.");
                conditionLock.acquire();
                System.out.println("t0 gets lock, and is going to sleep");
                condition.sleep();
                System.out.println("t0 wakes and wake condition");
                condition.wake();
                conditionLock.release();
                System.out.println("t0 releases lock, ends.");
            }
        });

        final KThread t1 = new KThread(new Runnable() {
            @Override
            public void run() {
                System.out.println("t1 begins.");
                conditionLock.acquire();
                System.out.println("t1 gets lock, and is going to sleep");
                condition.sleep();
                System.out.println("t1 wakes");
                condition.wake();
                conditionLock.release();
                System.out.println("t1 releases lock, ends.");
            }
        });

        final KThread t2 = new KThread(new Runnable() {
            @Override
            public void run() {
                System.out.println("t2 begins.");
                conditionLock.acquire();
                System.out.println("t2 gets lock, and is going to wake " +
                        "condition");
                condition.wake();
                conditionLock.release();
                System.out.println("t2 releases lock, ends.");
            }
        });

        t0.fork();
        t1.fork();
        t2.fork();
        t0.join();
        t1.join();
        t2.join();
        System.out.println("[Condition2][Test 1] ends.");
    }

    private void alarmTest1() {
        System.out.println("[Alarm][Test 1] begins.");
        Alarm alarm = new Alarm();
        Random random = new Random(new Date().getTime());

        for (int i = 0; i < 5; i++) {
            final KThread t0 = new KThread(new Runnable() {
                @Override
                public void run() {
                    int waitingTime = (int) (random.nextFloat() * 1e4);
                    long beginTime = Machine.timer().getTime();
                    System.out.println("Begin Alarm, expecting an end at " +
                            (waitingTime + beginTime) + " ticks.");
                    alarm.waitUntil(waitingTime);
                    long endTime = Machine.timer().getTime();
                    System.out.println("Alarm ends, exactly ends at" +
                            endTime + " ticks.\n");
                }
            });

            t0.fork();

            t0.join();
        }
        System.out.println("[Alarm][Test 1] ends.");
    }

    public void RunAllTest() {
        joinTest1();
        joinTest2();
        conditionTest1();
        // FIXME: bug here, it seems it's related to interrupt's enable status
        condition2Test1();
        alarmTest1();
    }
}
