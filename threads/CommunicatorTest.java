package nachos.threads;


import java.util.ArrayList;

/**
 * Created by Polo on 3/31/2016.
 */
public class CommunicatorTest {
    private void communicatorTest1() {
        class Recorder {
            public int[] speaker;
            public int[] listener;

            public Recorder(int len) {
                speaker = new int[len];
                listener = new int[len];
            }

            public void check() {
                for (int i = 0; i < speaker.length; i++)
                    if (speaker[i] == 1 && listener[i] == 0)
                        System.out.println("[ERROR] speaker ends before listener ends, " + i);
            }
        }
        final Communicator communicator = new Communicator();
        final int[] tester = new int[]{1,1,1,1,0,0,0,0,
                        1,1,1,1,0,0,1,1,0,0,0,0,1,0,1,0,1,0,
                        0,0,0,0,1,1,1,1,1,1,1,1,0,0,0,0,
                        0,0,0,0,0,0,0,0,1,1,1,1,1,1,1,1,
                        1,1,1,1,1,1,1,1,0,0,0,0,0,0,0,0,
                        0,0,1,1,0,0,1,1,0,0,1,1,0,0,1,1,
                        0,0,0,0,1,1,1,1,0,0,0,0,1,1,1,1,
                        0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,
                        1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,
                        0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0
                    };
        final Recorder recorder = new Recorder(tester.length);
        ArrayList<KThread> threads = new ArrayList<>();
        for (int i = 0; i < tester.length; i++) {
            final int i_ = i;
            KThread t = new KThread(new Runnable() {
                @Override
                public void run() {
                    if (tester[i_] == 0) {
                        // System.out.println("Speaker " + i_ + " speak begin " + "with " + i_ + ".");
                        communicator.speak(i_);
                        recorder.speaker[i_] = 1;
                        System.out.println("Speaker " + i_ + " speak ends with  \t\t\t\t" + i_);
                    } else {
                        // System.out.println("Listener " + i_ + " listen begin.");
                        int a = communicator.listen();
                        recorder.listener[a] = 1;
                        System.out.println("Listener " + i_ + " listen ends " + "with \t\t\t\t" + a);
                    }
                    // System.out.println("[INFO] check");
                    recorder.check();
                }
            });
            t.fork();
            threads.add(t);
        }
        for (KThread t : threads)
            t.join();
    }

    public void RunAllTest() {
        communicatorTest1();
    }
}
