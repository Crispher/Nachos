package nachos.userprog;

import nachos.machine.*;
import nachos.threads.KThread;
import nachos.threads.ThreadedKernel;

import javax.crypto.Mac;
import java.io.EOFException;
import java.util.LinkedList;

/**
 * Encapsulates the state of a user process that is not contained in its
 * user thread (or threads). This includes its address translation state, a
 * file table, and information about the program being executed.
 * <p>
 * <p>
 * This class is extended by other classes to support additional functionality
 * (such as additional syscalls).
 *
 * @see nachos.vm.VMProcess
 * @see nachos.network.NetProcess
 */
public class UserProcess {
    private static final int
            syscallHalt = 0,
            syscallExit = 1,
            syscallExec = 2,
            syscallJoin = 3,
            syscallCreate = 4,
            syscallOpen = 5,
            syscallRead = 6,
            syscallWrite = 7,
            syscallClose = 8,
            syscallUnlink = 9;
    private static final int pageSize = Processor.pageSize;
    private static final char dbgProcess = 'a';
    /**
     * The number of pages in the program's stack.
     */
    protected final int stackPages = 8;
    /**
     * The program being run by this process.
     */
    protected Coff coff;
    /**
     * This process's page table.
     */
    protected TranslationEntry[] pageTable;
    /**
     * The number of contiguous pages occupied by the program.
     */
    protected int numPages;
    private int initialPC, initialSP;
    private int argc, argv;

    private static int processCount = 1;
    private static final int ROOT_PROCESS = 1;
    private int processId;
    private int exitStatus;
    private UThread thread;
    private UserProcess parentProcess;
    private LinkedList<UserProcess> children;
    private static final int MAX_FILE = 100;
    private static final int UNHANDLED_EXCEPTION = -1234;
    private OpenFile[] fileList;
    private static final int BUFFER_SIZE = 1 << 9;

    /**
     * Allocate a new process.
     */
    public UserProcess() {
        boolean prevStatus = Machine.interrupt().disable();
        processId = processCount++;
        fileList = new OpenFile[MAX_FILE];
        fileList[0] = UserKernel.console.openForReading();
        fileList[1] = UserKernel.console.openForWriting();
        children = new LinkedList<UserProcess>();
        exitStatus = UNHANDLED_EXCEPTION;
        Machine.interrupt().restore(prevStatus);
    }

    /**
     * Allocate and return a new process of the correct class. The class name
     * is specified by the <tt>nachos.conf</tt> key
     * <tt>Kernel.processClassName</tt>.
     *
     * @return a new process of the correct class.
     */
    public static UserProcess newUserProcess() {
        return (UserProcess) Lib.constructObject(Machine.getProcessClassName());
    }

    /**
     * Execute the specified program with the specified arguments. Attempts to
     * load the program, and then forks a thread to run it.
     *
     * @param name the name of the file containing the executable.
     * @param args the arguments to pass to the executable.
     * @return <tt>true</tt> if the program was successfully executed.
     */
    public boolean execute(String name, String[] args) {
        if (!load(name, args))
            return false;

        //new UThread(this).setName(name).fork();	// original
        thread = new UThread(this);
        thread.setName(name).fork();

        return true;
    }

    /**
     * Save the state of this process in preparation for a context switch.
     * Called by <tt>UThread.saveState()</tt>.
     */
    public void saveState() {
    }

    /**
     * Restore the state of this process after a context switch. Called by
     * <tt>UThread.restoreState()</tt>.
     */
    public void restoreState() {
        Machine.processor().setPageTable(pageTable);
    }

    /**
     * Read a null-terminated string from this process's virtual memory. Read
     * at most <tt>maxLength + 1</tt> bytes from the specified address, search
     * for the null terminator, and convert it to a <tt>java.lang.String</tt>,
     * without including the null terminator. If no null terminator is found,
     * returns <tt>null</tt>.
     *
     * @param vaddr     the starting virtual address of the null-terminated
     *                  string.
     * @param maxLength the maximum number of characters in the string,
     *                  not including the null terminator.
     * @return the string read, or <tt>null</tt> if no null terminator was
     * found.
     */
    public String readVirtualMemoryString(int vaddr, int maxLength) {
        Lib.assertTrue(maxLength >= 0);

        byte[] bytes = new byte[maxLength + 1];

        int bytesRead = readVirtualMemory(vaddr, bytes);

        for (int length = 0; length < bytesRead; length++) {
            if (bytes[length] == 0)
                return new String(bytes, 0, length);
        }

        return null;
    }

    /**
     * Transfer data from this process's virtual memory to all of the specified
     * array. Same as <tt>readVirtualMemory(vaddr, data, 0, data.length)</tt>.
     *
     * @param vaddr the first byte of virtual memory to read.
     * @param data  the array where the data will be stored.
     * @return the number of bytes successfully transferred.
     */
    public int readVirtualMemory(int vaddr, byte[] data) {
        return readVirtualMemory(vaddr, data, 0, data.length);
    }

    /**
     * Transfer data from this process's virtual memory to the specified array.
     * This method handles address translation details. This method must
     * <i>not</i> destroy the current process if an error occurs, but instead
     * should return the number of bytes successfully copied (or zero if no
     * data could be copied).
     *
     * @param vaddr  the first byte of virtual memory to read.
     * @param data   the array where the data will be stored.
     * @param offset the first byte to write in the array.
     * @param length the number of bytes to transfer from virtual memory to
     *               the array.
     * @return the number of bytes successfully transferred.
     */
    public int readVirtualMemory(int vaddr, byte[] data, int offset,
                                 int length) {
        Lib.assertTrue(offset >= 0 && length >= 0 && offset + length <= data.length);

        byte[] memory = Machine.processor().getMemory();

        if (vaddr < 0 || vaddr >= numPages * pageSize)
            return 0;

        if (vaddr + length > numPages * pageSize)
            length = numPages * pageSize - vaddr;

        int amount = 0, firstPage = Machine.processor().pageFromAddress(vaddr),
                lastPage = Machine.processor().pageFromAddress(vaddr + length - 1);
        int start, end, pstart, pend;
        for (int page = firstPage; page <= lastPage; page++) {
            TranslationEntry t = pageTable[page];
            if (t != null) {
                // start and end are in the same page
                start = Math.max(page * pageSize, vaddr);
                end = Math.min((page + 1) * pageSize - 1, vaddr + length - 1);

                pstart = t.ppn * pageSize + Machine.processor().offsetFromAddress(start);
                pend  = t.ppn * pageSize + Machine.processor().offsetFromAddress(end);

                System.arraycopy(memory, pstart, data, offset + amount, pend - pstart + 1);
                amount += pend - pstart + 1;
                t.used = true;
            }
        }

        return amount;
    }

    /**
     * Transfer all data from the specified array to this process's virtual
     * memory.
     * Same as <tt>writeVirtualMemory(vaddr, data, 0, data.length)</tt>.
     *
     * @param vaddr the first byte of virtual memory to write.
     * @param data  the array containing the data to transfer.
     * @return the number of bytes successfully transferred.
     */
    public int writeVirtualMemory(int vaddr, byte[] data) {
        return writeVirtualMemory(vaddr, data, 0, data.length);
    }

    /**
     * Transfer data from the specified array to this process's virtual memory.
     * This method handles address translation details. This method must
     * <i>not</i> destroy the current process if an error occurs, but instead
     * should return the number of bytes successfully copied (or zero if no
     * data could be copied).
     *
     * @param vaddr  the first byte of virtual memory to write.
     * @param data   the array containing the data to transfer.
     * @param offset the first byte to transfer from the array.
     * @param length the number of bytes to transfer from the array to
     *               virtual memory.
     * @return the number of bytes successfully transferred.
     */
    public int writeVirtualMemory(int vaddr, byte[] data, int offset,
                                  int length) {
        Lib.assertTrue(offset >= 0 && length >= 0 && offset + length <= data.length);

        byte[] memory = Machine.processor().getMemory();

        // for now, just assume that virtual addresses equal physical addresses
        if (vaddr < 0 || vaddr >= numPages * pageSize)
            return 0;

        if (vaddr + length > numPages * pageSize)
            length = numPages * pageSize - vaddr;

        int amount = 0, firstPage = Machine.processor().pageFromAddress(vaddr),
                lastPage = Machine.processor().pageFromAddress(vaddr + length - 1);
        int start, end, pstart, pend;
        for (int page = firstPage; page <= lastPage; page++) {
            TranslationEntry t = pageTable[page];
            if (t != null) {
                // start and end are in the same page
                start = Math.max(page * pageSize, vaddr);
                end = Math.min((page + 1) * pageSize - 1, vaddr + length - 1);

                pstart = t.ppn * pageSize + Machine.processor().offsetFromAddress(start);
                pend  = t.ppn * pageSize + Machine.processor().offsetFromAddress(end);

                System.arraycopy(data, offset + amount, memory, pstart, pend - pstart + 1);
                amount += pend - pstart + 1;
                t.used = true;
                t.dirty = true;
            }
        }

        return amount;
    }

    /**
     * Load the executable with the specified name into this process, and
     * prepare to pass it the specified arguments. Opens the executable, reads
     * its header information, and copies sections and arguments into this
     * process's virtual memory.
     *
     * @param name the name of the file containing the executable.
     * @param args the arguments to pass to the executable.
     * @return <tt>true</tt> if the executable was successfully loaded.
     */
    private boolean load(String name, String[] args) {
        Lib.debug(dbgProcess, "UserProcess.load(\"" + name + "\")");

        OpenFile executable = ThreadedKernel.fileSystem.open(name, false);
        if (executable == null) {
            Lib.debug(dbgProcess, "\topen failed");
            return false;
        }

        try {
            coff = new Coff(executable);
        } catch (EOFException e) {
            executable.close();
            Lib.debug(dbgProcess, "\tcoff load failed");
            return false;
        }

        // make sure the sections are contiguous and start at page 0
        numPages = 0;
        for (int s = 0; s < coff.getNumSections(); s++) {
            CoffSection section = coff.getSection(s);
            if (section.getFirstVPN() != numPages) {
                coff.close();
                Lib.debug(dbgProcess, "\tfragmented executable");
                return false;
            }
            numPages += section.getLength();
        }

        // make sure the argv array will fit in one page
        byte[][] argv = new byte[args.length][];
        int argsSize = 0;
        for (int i = 0; i < args.length; i++) {
            argv[i] = args[i].getBytes();
            // 4 bytes for argv[] pointer; then string plus one for null byte
            argsSize += 4 + argv[i].length + 1;
        }
        if (argsSize > pageSize) {
            coff.close();
            Lib.debug(dbgProcess, "\targuments too long");
            return false;
        }

        // program counter initially points at the program entry point
        initialPC = coff.getEntryPoint();

        // next comes the stack; stack pointer initially points to top of it
        numPages += stackPages;
        initialSP = numPages * pageSize;

        // and finally reserve 1 page for arguments
        numPages++;

        if (!loadSections())
            return false;

        // store arguments in last page
        int entryOffset = (numPages - 1) * pageSize;
        int stringOffset = entryOffset + args.length * 4;

        this.argc = args.length;
        this.argv = entryOffset;

        for (int i = 0; i < argv.length; i++) {
            byte[] stringOffsetBytes = Lib.bytesFromInt(stringOffset);
            Lib.assertTrue(writeVirtualMemory(entryOffset, stringOffsetBytes) == 4);
            entryOffset += 4;
            Lib.assertTrue(writeVirtualMemory(stringOffset, argv[i]) ==
                    argv[i].length);
            stringOffset += argv[i].length;
            Lib.assertTrue(writeVirtualMemory(stringOffset, new byte[]{0}) == 1);
            stringOffset += 1;
        }

        return true;
    }

    /**
     * Allocates memory for this process, and loads the COFF sections into
     * memory. If this returns successfully, the process will definitely be
     * run (this is the last step in process initialization that can fail).
     *
     * @return <tt>true</tt> if the sections were successfully loaded.
     */
    protected boolean loadSections() {
        UserKernel.pageLock.acquire();
        try {
            if (numPages > UserKernel.freePhysicalPages.size()) {
                coff.close();
                Lib.debug(dbgProcess, "\tinsufficient physical memory");
                return false;
            }

            pageTable = new TranslationEntry[numPages];
            int nextPageTableIndex = 0;
            // load sections
            for (int s = 0; s < coff.getNumSections(); s++) {
                CoffSection section = coff.getSection(s);

                Lib.debug(dbgProcess, "\tinitializing " + section.getName()
                        + " section (" + section.getLength() + " pages)");

                for (int i = 0; i < section.getLength(); i++) {
                    int vpn = section.getFirstVPN() + i;

                    int ppn = UserKernel.freePhysicalPages.pollFirst();
                    pageTable[nextPageTableIndex++] = new TranslationEntry(vpn,
                            ppn, true, section.isReadOnly(), false, false);

                    section.loadPage(i, ppn);
                }
            }

            for (int s = 0; s < stackPages + 1; s++) {
                int vpn = nextPageTableIndex, ppn = UserKernel.freePhysicalPages.pollFirst();
                pageTable[nextPageTableIndex] = new TranslationEntry(vpn,
                        ppn, true, false, false, false);
                nextPageTableIndex++;
            }
            
            return true;
        } finally {
            UserKernel.pageLock.release();
        }
    }

    /**
     * Release any resources allocated by <tt>loadSections()</tt>.
     */
    protected void unloadSections() {
        UserKernel.pageLock.acquire();
        try {
            for (int i = 0; i < pageTable.length; i++) {
                UserKernel.freePhysicalPages.addLast(pageTable[i].ppn);
                pageTable[i] = null;
            }
        } finally {
            UserKernel.pageLock.release();
        }
        coff.close();
    }

    /**
     * Initialize the processor's registers in preparation for running the
     * program loaded into this process. Set the PC register to point at the
     * start function, set the stack pointer register to point at the top of
     * the stack, set the A0 and A1 registers to argc and argv, respectively,
     * and initialize all other registers to 0.
     */
    public void initRegisters() {
        Processor processor = Machine.processor();

        // by default, everything's 0
        for (int i = 0; i < processor.numUserRegisters; i++)
            processor.writeRegister(i, 0);

        // initialize PC and SP according
        processor.writeRegister(Processor.regPC, initialPC);
        processor.writeRegister(Processor.regSP, initialSP);

        // initialize the first two argument registers to argc and argv
        processor.writeRegister(Processor.regA0, argc);
        processor.writeRegister(Processor.regA1, argv);
    }

    /**
     * Handle the halt() system call.
     */
    private int handleHalt() {

        Machine.halt();

        Lib.assertNotReached("Machine.halt() did not halt machine!");
        return 0;
    }

    /**
     * Handle a syscall exception. Called by <tt>handleException()</tt>. The
     * <i>syscall</i> argument identifies which syscall the user executed:
     * <p>
     * <table>
     * <tr><td>syscall#</td><td>syscall prototype</td></tr>
     * <tr><td>0</td><td><tt>void halt();</tt></td></tr>
     * <tr><td>1</td><td><tt>void exit(int status);</tt></td></tr>
     * <tr><td>2</td><td><tt>int  exec(char *name, int argc, char **argv);
     * </tt></td></tr>
     * <tr><td>3</td><td><tt>int  join(int pid, int *status);</tt></td></tr>
     * <tr><td>4</td><td><tt>int  creat(char *name);</tt></td></tr>
     * <tr><td>5</td><td><tt>int  open(char *name);</tt></td></tr>
     * <tr><td>6</td><td><tt>int  read(int fd, char *buffer, int size);
     * </tt></td></tr>
     * <tr><td>7</td><td><tt>int  write(int fd, char *buffer, int size);
     * </tt></td></tr>
     * <tr><td>8</td><td><tt>int  close(int fd);</tt></td></tr>
     * <tr><td>9</td><td><tt>int  unlink(char *name);</tt></td></tr>
     * </table>
     *
     * @param syscall the syscall number.
     * @param a0      the first syscall argument.
     * @param a1      the second syscall argument.
     * @param a2      the third syscall argument.
     * @param a3      the fourth syscall argument.
     * @return the value to be returned to the user.
     */
    public int handleSyscall(int syscall, int a0, int a1, int a2, int a3) {
        switch (syscall) {
            case syscallHalt:
                return handleHalt();
            case syscallCreate:
                return handleOpenOrCreate(a0, true);
            case syscallOpen:
                return handleOpenOrCreate(a0, false);
            case syscallRead:
                return handleRead(a0, a1, a2);
            case syscallWrite:
                return handleWrite(a0, a1, a2);
            case syscallClose:
                return handleClose(a0);
            case syscallUnlink:
                return handleUnlink(a0);
            case syscallJoin:
                return handleJoin(a0, a1);
            case syscallExec:
                return handleExec(a0, a1, a2);
            case syscallExit:
                return handleExit(a0);
            default:
                Lib.debug(dbgProcess, "Unknown syscall " + syscall);
                Lib.assertNotReached("Unknown system call!");
        }
        return 0;
    }

    /**
     * Handle a user exception. Called by
     * <tt>UserKernel.exceptionHandler()</tt>. The
     * <i>cause</i> argument identifies which exception occurred; see the
     * <tt>Processor.exceptionZZZ</tt> constants.
     *
     * @param cause the user exception that occurred.
     */
    public void handleException(int cause) {
        Processor processor = Machine.processor();

        switch (cause) {
            case Processor.exceptionSyscall:
                int result = handleSyscall(processor.readRegister(Processor.regV0),
                        processor.readRegister(Processor.regA0),
                        processor.readRegister(Processor.regA1),
                        processor.readRegister(Processor.regA2),
                        processor.readRegister(Processor.regA3)
                );
                processor.writeRegister(Processor.regV0, result);
                processor.advancePC();
                break;

            default:
                Lib.debug(dbgProcess, "Unexpected exception: " +
                        Processor.exceptionNames[cause]);
                handleExit(UNHANDLED_EXCEPTION);
//                Lib.assertNotReached("Unexpected exception");
                // TODO: unload page table
        }
    }

    private int handleOpenOrCreate(int a, boolean isCreate) {
        if (a < 0)
            return -1;

        String fileName = readVirtualMemoryString(a, 256);
        if (fileName == null)
            return -1;

        int fileListIndex = -1, i = 0;
        for (; i < MAX_FILE; ++i)
            if (fileList[i] == null)
                break;
        if (i == MAX_FILE)
            return -1;

        // Now i should be the index in fileList
        if (!UserKernel.fileManager.open(fileName))    // Checking whether the file should be unlinked
            return -1;

        OpenFile openFile = ThreadedKernel.fileSystem.open(fileName, isCreate);
        if (openFile == null)
            return -1;

        fileList[i] = openFile;
        return i;
    }

    private int handleClose(int a) {
        if (a < 0 || a >= MAX_FILE) return -1;
        OpenFile openFile = fileList[a];
        if (openFile == null) return -1;
        String fileName = openFile.getName();
        openFile.close();
        fileList[a] = null;
        //if ((fileName != "SynchConsole") && (!UserKernel.fileManager.close(fileName)))
        if ((openFile.getFileSystem() != null) && (!UserKernel.fileManager.close(fileName)))    // After discussion with LYP
            return -1;
        else
            return 0;
    }

    private int handleUnlink(int a) {
        if (a < 0)
            return -1;

        String fileName = readVirtualMemoryString(a, 256);
        if (fileName == null)
            return -1;
        if (!UserKernel.fileManager.unlink(fileName))
            return -1;

        else return 0;
    }

    private int handleRead(int a0, int a1, int a2) {
        if (a0 < 0 || a0 >= MAX_FILE || a1 < 0 || a2 < 0)
            return -1;

        OpenFile openFile = fileList[a0];
        if (openFile == null)
            return -1;

        byte[] buffer = new byte[BUFFER_SIZE];
        int readCount = 0, readLength, readLengthActual, writeLengthActual;
        while (a2 > 0) {
            readLength = a2 > BUFFER_SIZE ? BUFFER_SIZE : a2;
            readLengthActual = openFile.read(buffer, 0, readLength);
            if (readLengthActual == -1)
                return -1;

            readCount += readLengthActual;
            writeLengthActual = writeVirtualMemory(a1, buffer, 0, readLengthActual);
            if (readLengthActual != writeLengthActual)
                return -1;

            a2 -= readLengthActual;
            a1 += readLengthActual;
            if (readLengthActual < readLength)
                break;
        }
        return readCount;
    }

    private int handleWrite(int a0, int a1, int a2) {
        if (a0 < 0 || a0 >= MAX_FILE || a1 < 0 || a2 < 0)
            return -1;

        OpenFile openFile = fileList[a0];
        if (openFile == null)
            return -1;

        byte[] buffer = new byte[BUFFER_SIZE];
        int writeCount = 0, writeLengthActual, readLength, readLengthActual;
        while (a2 > 0) {
            readLength = a2 > BUFFER_SIZE ? BUFFER_SIZE : a2;
            readLengthActual = readVirtualMemory(a1, buffer, 0, readLength);
            if (readLengthActual < readLength)
                return -1;

            writeLengthActual = openFile.write(buffer, 0, readLength);
            if (writeLengthActual != readLength)
                return -1;    // Different from handleRead, this is considered an error.

            a1 += readLength;
            a2 -= readLength;
            writeCount += readLength;
        }
        return writeCount;
    }

    private int handleExec(int a0, int a1, int a2) {
        if (a0 < 0 || a1 < 0 || a2 < 0)
            return -1;
        String fileName = readVirtualMemoryString(a0, 256);
        if (fileName == null || !fileName.toLowerCase().endsWith(".coff"))
            return -1;

        String args[] = new String[a1];
        byte[] bytes = new byte[4];
        int byteRead, argsAddress;
        for (int i = 0; i < a1; ++i) {
            byteRead = readVirtualMemory(a2 + (i << 2), bytes);
            if (byteRead != 4)
                return -1;

            argsAddress = Lib.bytesToInt(bytes, 0);
            args[i] = readVirtualMemoryString(argsAddress, 256);
            if (args[i] == null)
                return -1;
        }

        UserProcess child = UserProcess.newUserProcess();
        child.parentProcess = this;
        if (child.execute(fileName, args)) {
            children.add(child);
            return child.processId;
        } else
            return -1;
    }

    private int handleExit(int a) {
        exitStatus = a;

        unloadSections();
        for (int i = 0; i < MAX_FILE; ++i) {    // should i start from 2 instead?
            if (fileList[i] != null) {
                handleClose(i);
                fileList[i] = null;    // this line seems to be redundant
            }
        }
        while (children.size() > 0)
            children.removeFirst().parentProcess = null;

        if (processId == ROOT_PROCESS)
            Kernel.kernel.terminate();
        else
            KThread.finish();

        return exitStatus;
    }

    private int handleJoin(int a0, int a1) {
        if (a0 < 0 || a1 < 0)
            return -1;

        UserProcess child = null;
        int childrenNum = children.size(), i = 0;
        for (; i < childrenNum; ++i) {
            child = children.get(i);
            if (child.processId == a0)
                break;
        }
        if (i == childrenNum) return -1;
        // now child is the child process
        children.remove(child);
        if (child.thread != null)
            child.thread.join();

        int childExitStatus = child.exitStatus;
        if (childExitStatus == UNHANDLED_EXCEPTION)
            return 0;    // no writing to memory then

        byte[] bytes = Lib.bytesFromInt(childExitStatus);
        int byteWritten = writeVirtualMemory(a1, bytes);
        if (byteWritten == 4)
            return 1;
        return 0;
    }
}
