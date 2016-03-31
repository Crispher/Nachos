package nachos.threads;

import javax.sql.rowset.spi.SyncProvider;
import java.util.ArrayList;

/**
 * Created by Polo on 3/31/2016.
 */
public class CommunicatorTest {
    private void communicatorTest1() {
        final Communicator communicator = new Communicator();
        final int[] tester = new int[]{1,0,0,1,0,1,1,0,1,0};
        ArrayList<KThread> threads = new ArrayList<>();
        for (int i = 0; i < tester.length; i++) {
            final int i_ = i;
            KThread t = new KThread(new Runnable() {
                @Override
                public void run() {
                    if (tester[i_] == 0) {
                        System.out.println("Speaker " + i_ + " speak begin " +
                                "with " + i_ + ".");
                        communicator.speak(i_);
                        System.out.println("Speaker " + i_ + " speak ends.");
                    } else {
                        System.out.println("Listener " + i_ + " listen begin.");
                        int a = communicator.listen();
                        System.out.println("Listener " + i_ + " listen ends " +
                                "with " + a + ".");
                    }
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
