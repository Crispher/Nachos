package nachos.threads;

import nachos.machine.Machine;
/**
 * A <i>communicator</i> allows threads to synchronously exchange 32-bit
 * messages. Multiple threads can be waiting to <i>speak</i>,
 * and multiple threads can be waiting to <i>listen</i>. But there should never
 * be a time when both a speaker and a listener are waiting, because the two
 * threads can be paired off at this point.
 */
public class Communicator {
    public Lock lock;
    public Condition speakerCondition, listenerCondition, waitSpeakerCondition, waitListenerCondition;

    private int numOfListener, numOfSpeaker, data;
    private boolean dataReady;
    /**
     * Allocate a new communicator.
     */
    public Communicator() {
        lock = new Lock();
        speakerCondition = new Condition(lock);
        listenerCondition = new Condition(lock);
        waitSpeakerCondition = new Condition(lock);
        waitListenerCondition = new Condition(lock);

        numOfListener = 0;
        numOfSpeaker = 0;
        data = -1;
    }

    /**
     * Wait for a thread to listen through this communicator, and then transfer
     * <i>word</i> to the listener.
     * <p>
     * <p>
     * Does not return until this thread is paired up with a listening thread.
     * Exactly one listener should receive <i>word</i>.
     *
     * @param word the integer to transfer.
     */
    public void speak(int word) {
        lock.acquire();

        numOfSpeaker++;
        while (numOfListener < 1 || data != -1) {
            speakerCondition.sleep();
        }

        data = word;
        listenerCondition.wakeAll();
        waitSpeakerCondition.wakeAll();
        waitListenerCondition.sleep();

        lock.release();
    }

    /**
     * Wait for a thread to speak through this communicator, and then return
     * the <i>word</i> that thread passed to <tt>speak()</tt>.
     *
     * @return the integer transferred.
     */
     public int listen() {
        int value = 0;
        lock.acquire();

        numOfListener++;
        while (numOfSpeaker < 1 || (data == -1)) {
            speakerCondition.wake();
            listenerCondition.sleep();
        }

        while (data == -1)
            waitSpeakerCondition.sleep();

        value = data;
        data = -1;
        numOfListener--;
        numOfSpeaker--;

        waitListenerCondition.wake();
        if (numOfSpeaker > 0)
            speakerCondition.wake();

        lock.release();
        return value;
    }
}
