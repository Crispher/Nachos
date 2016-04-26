package nachos.userprog;

import nachos.machine.Coff;
import nachos.machine.Lib;
import nachos.machine.Machine;
import nachos.machine.Processor;
import nachos.threads.KThread;
import nachos.threads.ThreadedKernel;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

class Lock {
    ReentrantLock l = new ReentrantLock();

    public void acquire() {
        l.lock();
    }

    public void release() {
        l.unlock();
    }
}

/**
 * A kernel that can support multiple user processes.
 */
public class UserKernel extends ThreadedKernel {
    /**
     * Globally accessible reference to the synchronized console.
     */
    public static SynchConsole console;
    // dummy variables to make javac smarter
    private static Coff dummy1 = null;

    // linked list of free physical pages
    public static Lock pageLock = new Lock(); // lock for modifying physical pages
    public static LinkedList<Integer> freePhysicalPages = new LinkedList<>();

    /**
     * Allocate a new user kernel.
     */
    public UserKernel() {
        super();
    }

    /**
     * Returns the current process.
     *
     * @return the current process, or <tt>null</tt> if no process is current.
     */
    public static UserProcess currentProcess() {
        if (!(KThread.currentThread() instanceof UThread))
            return null;

        return ((UThread) KThread.currentThread()).process;
    }

    /**
     * Initialize this kernel. Creates a synchronized console and sets the
     * processor's exception handler.
     */
    public void initialize(String[] args) {
        super.initialize(args);

        console = new SynchConsole(Machine.console());

        Machine.processor().setExceptionHandler(new Runnable() {
            public void run() {
                exceptionHandler();
            }
        });

        if (freePhysicalPages.size() == 0)
            for (int i = 0; i < Machine.processor().getNumPhysPages(); i++)
                freePhysicalPages.add(i);
    }

    /**
     * Test the console device.
     */
    public void selfTest() {
        super.selfTest();

        System.out.println("Testing the console device. Typed characters");
        System.out.println("will be echoed until q is typed.");

        char c;

        do {
            c = (char) console.readByte(true);
            console.writeByte(c);
        }
        while (c != 'q');

        System.out.println("");
    }

    /**
     * The exception handler. This handler is called by the processor whenever
     * a user instruction causes a processor exception.
     * <p>
     * <p>
     * When the exception handler is invoked, interrupts are enabled, and the
     * processor's cause register contains an integer identifying the cause of
     * the exception (see the <tt>exceptionZZZ</tt> constants in the
     * <tt>Processor</tt> class). If the exception involves a bad virtual
     * address (e.g. page fault, TLB miss, read-only, bus error, or address
     * error), the processor's BadVAddr register identifies the virtual address
     * that caused the exception.
     */
    public void exceptionHandler() {
        Lib.assertTrue(KThread.currentThread() instanceof UThread);

        UserProcess process = ((UThread) KThread.currentThread()).process;
        int cause = Machine.processor().readRegister(Processor.regCause);
        process.handleException(cause);
    }

    /**
     * Start running user programs, by creating a process and running a shell
     * program in it. The name of the shell program it must run is returned by
     * <tt>Machine.getShellProgramName()</tt>.
     *
     * @see nachos.machine.Machine#getShellProgramName
     */
    public void run() {
        super.run();

        UserProcess process = UserProcess.newUserProcess();

        String shellProgram = Machine.getShellProgramName();
        Lib.assertTrue(process.execute(shellProgram, new String[]{}));

        KThread.currentThread().finish();
    }

    /**
     * Terminate this kernel. Never returns.
     */
    public void terminate() {
        super.terminate();
    }

    public static class FileManager {
        Map<String, FileRecord> fileMap = new HashMap<String, FileRecord>();

        private static class FileRecord {
            int number;    // Last bit of number represents whether that file should be unlinked, other part of it is the number of processes accessing it (start from 0, of course)

            public FileRecord() {
                number = 0;
            }

            public boolean unlinked() {
                return ((number & 01) != 0);
            }
        }

        Lock mutex = new Lock();

        boolean open(String fileName) {
            mutex.acquire();
            if (!fileMap.containsKey(fileName)) {
                FileRecord record = new FileRecord();
                fileMap.put(fileName, record);
                mutex.release();
                return true;
            }
            FileRecord record = fileMap.get(fileName);
            if (record.unlinked()) {
                mutex.release();
                return false;
            }
            record.number += 2;
            mutex.release();
            return true;
        }

        boolean close(String fileName) {
            mutex.acquire();
            if (!fileMap.containsKey(fileName)) {
                mutex.release();
                return false;
            }
            FileRecord record = fileMap.get(fileName);
            if (record.number <= 1) {
                if (record.number == 1)
                    UserKernel.fileSystem.remove(fileName);// nobody else is using that file, and it should be deleted.
                fileMap.remove(record);    // simply remove this file, without deleting it.
            } else
                record.number -= 2;    // decrease it's count
            mutex.release();
            return true;
        }

        boolean unlink(String fileName) {
            mutex.acquire();
            if (!fileMap.containsKey(fileName)) {
                mutex.release();
                return false;
            }
            FileRecord record = fileMap.get(fileName);
            if (record.number <= 1)    // should be deleted
            {
                UserKernel.fileSystem.remove(fileName);
                fileMap.remove(record);
            } else {
                record.number = (record.number - 2) | 1;
            }
            mutex.release();
            return true;
        }
    }

    public static FileManager fileManager = new FileManager();
}
