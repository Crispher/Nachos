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

    private void conditionTest2() {
        Lock conditionLock = new Lock();
        Condition condition = new Condition(conditionLock);
        Condition condition2 = new Condition(conditionLock);

        class final_int {
            public int number = 0;
        }
        final final_int item = new final_int();
        final final_int minus_item = new final_int();

        ArrayList<Runnable> runnables = new ArrayList<>();

        Runnable resumer1 = new Runnable() {
            @Override
            public void run() {
                conditionLock.acquire();
                while (item.number == 0)
                    condition.sleep();
                item.number--;
                System.out.println("[item] resumed one item, num: "
                        + item.number);
                condition.wakeAll();
                conditionLock.release();
            }
        };

        Runnable resumer2 = new Runnable() {
            @Override
            public void run() {
                conditionLock.acquire();
                while (minus_item.number == 0)
                    condition2.sleep();
                minus_item.number++;
                System.out.println("[minus_item] resumed one item, num: "
                        + minus_item.number);
                condition2.wakeAll();
                conditionLock.release();
            }
        };

        Runnable producer1 = new Runnable() {
            @Override
            public void run() {
                conditionLock.acquire();
                while (item.number != 0)
                    condition.sleep();
                item.number += 4;
                System.out.println("[item] producer1 produces 4 items, num: "
                        + item.number);
                condition.wakeAll();
                conditionLock.release();
            }
        };

        Runnable producer2 = new Runnable() {
            @Override
            public void run() {
                conditionLock.acquire();
                while (minus_item.number != 0)
                    condition2.sleep();
                minus_item.number -= 4;
                System.out.println("[minus_item] producer1 produces -4 items,"
                        + " num: " + minus_item.number);
                condition2.wakeAll();
                conditionLock.release();
            }
        };

        runnables.add(resumer1);
        runnables.add(producer1);
        runnables.add(resumer2);
        runnables.add(producer2);

        ArrayList<KThread> threads = new ArrayList<>();
        for (int i = 0; i < 20; i++)
            for (int j = 0; j < 4; j++) {
                int k = j % 2 == 0 ? 4 : 1;
                for (int kk = 0; kk < k; kk++)
                   threads.add(new KThread(runnables.get(j)));
            }

        for (KThread t : threads)
            t.fork();

        for (KThread t : threads)
            t.join();
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

    private void condition2Test2() {
        Lock conditionLock = new Lock();
        Condition2 condition = new Condition2(conditionLock);
        Condition2 condition2 = new Condition2(conditionLock);

        class final_int {
            public int number = 0;
        }
        final final_int item = new final_int();
        final final_int minus_item = new final_int();

        ArrayList<Runnable> runnables = new ArrayList<>();

        Runnable resumer1 = new Runnable() {
            @Override
            public void run() {
                conditionLock.acquire();
                while (item.number == 0)
                    condition.sleep();
                item.number--;
                System.out.println("[item] resumed one item, num: "
                        + item.number);
                condition.wakeAll();
                conditionLock.release();
            }
        };

        Runnable resumer2 = new Runnable() {
            @Override
            public void run() {
                conditionLock.acquire();
                while (minus_item.number == 0)
                    condition2.sleep();
                minus_item.number++;
                System.out.println("[minus_item] resumed one item, num: "
                        + minus_item.number);
                condition2.wakeAll();
                conditionLock.release();
            }
        };

        Runnable producer1 = new Runnable() {
            @Override
            public void run() {
                conditionLock.acquire();
                while (item.number != 0)
                    condition.sleep();
                item.number += 4;
                System.out.println("[item] producer1 produces 4 items, num: "
                        + item.number);
                condition.wakeAll();
                conditionLock.release();
            }
        };

        Runnable producer2 = new Runnable() {
            @Override
            public void run() {
                conditionLock.acquire();
                while (minus_item.number != 0)
                    condition2.sleep();
                minus_item.number -= 4;
                System.out.println("[minus_item] producer1 produces -4 items,"
                        + " num: " + minus_item.number);
                condition2.wakeAll();
                conditionLock.release();
            }
        };

        runnables.add(resumer1);
        runnables.add(producer1);
        runnables.add(resumer2);
        runnables.add(producer2);

        ArrayList<KThread> threads = new ArrayList<>();
        for (int i = 0; i < 20; i++)
            for (int j = 0; j < 4; j++) {
                int k = j % 2 == 0 ? 4 : 1;
                for (int kk = 0; kk < k; kk++)
                    threads.add(new KThread(runnables.get(j)));
            }

        for (KThread t : threads)
            t.fork();

        for (KThread t : threads)
            t.join();
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
        condition2Test1();
        conditionTest2();
        condition2Test2();
        
        alarmTest1();
    }
}
