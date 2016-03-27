package nachos.threads;

import nachos.machine.*;

/**
 * A <i>communicator</i> allows threads to synchronously exchange 32-bit
 * messages. Multiple threads can be waiting to <i>speak</i>,
 * and multiple threads can be waiting to <i>listen</i>. But there should never
 * be a time when both a speaker and a listener are waiting, because the two
 * threads can be paired off at this point.
 */
public class Communicator {
    /**
     * Allocate a new communicator.
     */
    public Communicator() {
        // modified by Crispher
        lock = new Lock();
        speakerCondition = new Condition(lock);
        listenerCondition = new Condition(lock);
        // end
    }

    /**
     * Wait for a thread to listen through this communicator, and then transfer
     * <i>word</i> to the listener.
     * <p>
     * <p>
     * Does not return until this thread is paired up with a listening thread.
     * Exactly one listener should receive <i>word</i>.
     *
     * @param    word    the integer to transfer.
     */
    public void speak(int word) {
        // modified by Crispher
        lock.acquire();
        while (numListeners == 0) {
            speakerCondition.sleep();
        }
        data = word;
        dataReady = true;
        listenerCondition.wake();   // it's up to the waken listener to acquire the lock before the data is consumed
        lock.release();
        // end
    }

    /**
     * Wait for a thread to speak through this communicator, and then return
     * the <i>word</i> that thread passed to <tt>speak()</tt>.
     *
     * @return the integer transferred.
     */
    public int listen() {
        // modified by Crispher
        lock.acquire();
        while (!dataReady) {
            numListeners++;
            speakerCondition.wake();
            listenerCondition.sleep();
            numListeners--; // lock is reacquired before wake sleep() returns
        }
        dataReady = false;  // the data is consumed
        int word = data;
        lock.release();
        return word;
    }

    // modified by Crispher
    private int data;
    private boolean dataReady = false;
    private int  numListeners = 0; // number of threads on speakerCondition
    // above members are all protected by lock
    private Lock lock;
    private Condition speakerCondition, listenerCondition;
    // end
}
