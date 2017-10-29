/*
 *  Andrew Schneider
 *  Assignment 3 - Buddy Buffers
 */

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

public class BufferManager {

    private static final int MAX_BLOCK_SIZE = 512;
    private static final int NUM_BLOCKS = 10;

    private BuddyBuffer[][] bufferBlocks;

    public static void main(String[] args) {
        BufferManager manager = new BufferManager();
        ArrayList<Integer> bufferList = new ArrayList<>();

        try (BufferedWriter writer = new BufferedWriter(new FileWriter("output.txt"))){

            printMessage("Andrew Schneider", writer);
            printMessage("Assignment 3 - Buddy Buffers", writer);
            printMessage("\n", writer);
            printMessage("----------Begin Test Output----------", writer);


            printMessage("Expected:", writer);
            printMessage("\tIs tight: no", writer);
            printMessage("\tBuffers of size  511: 10", writer);
            printMessage("", writer);

            printMessage("Actual:", writer);
            printMessage(manager.createReport(), writer);

            printMessage("Requesting buffer of size 700, should fail.", writer);
            printMessage(manager.getBlock(700) == -2?"Failed to get block":"Block returned successfully", writer);
            printMessage("", writer);

            printMessage("Requesting buffer of size 7, should succeed", writer);
            bufferList.add(manager.getBlock(7));
            printMessage(bufferList.get(0)>=0?String.format("Buffer allocated, index returned: %d",bufferList.get(0)):"Failed to get buffer", writer);
            printMessage(manager.createReport(), writer);

            printMessage(String.format("Returning buffer at index %d", bufferList.get(0)), writer);
            manager.returnBuffer(bufferList.get(0));
            bufferList.remove(0);
            printMessage(manager.createReport(), writer);

            printMessage("Requesting 10 max size blocks", writer);
            for (int i = 0; i < NUM_BLOCKS; i++) {
                bufferList.add(manager.getBlock(511));
            }
            printMessage(manager.createReport(), writer);

            printMessage("Requesting another valid buffer, should fail", writer);
            bufferList.add(manager.getBlock(7));
            printMessage(String.format("Request returned %d%s", bufferList.get(bufferList.size()-1), bufferList.get(bufferList.size()-1)==-1?", success":", error"), writer);
            bufferList.remove(bufferList.size()-1);
            printMessage(manager.createReport(), writer);

            printMessage("Returning all buffers.", writer);
            for (int i = 0; i < bufferList.size(); i++){
                manager.returnBuffer(bufferList.get(i));
            }
            bufferList.clear();
            printMessage(manager.createReport(), writer);

            printMessage("Requesting three 7 word buffers", writer);
            for (int i = 0; i < 3; i++){
                bufferList.add(manager.getBlock(7));
            }
            printMessage(manager.createReport(), writer);

            printMessage("Returning first buffer, should have two free 7 word buffers", writer);
            manager.returnBuffer(bufferList.get(0));
            bufferList.remove(0);
            printMessage(manager.createReport(), writer);

            while (bufferList.size() > 0){
                manager.returnBuffer(bufferList.remove(0));
            }

            printMessage("Requesting random-sized buffers until manager returns -1", writer);
            int[] validSizes = new int[] {7,15,31,63,127,255,511};
            Random rng = new Random();
            for (int i = 0; i != -1; ){
                i = manager.getBlock(validSizes[rng.nextInt(validSizes.length)]);
                if (i != -1) bufferList.add(i);
            }
            printMessage(manager.createReport(), writer);

            printMessage("Returning all buffers", writer);
            while (bufferList.size() > 0){
                manager.returnBuffer(bufferList.remove(0));
            }
            printMessage(manager.createReport(), writer);

            printMessage("-----------End Test Output-----------", writer);
        }
        catch (IOException e){
            System.out.println("An error occurred writing to file");
            e.printStackTrace();
        }

    }

    public BufferManager(){
        bufferBlocks = new BuddyBuffer[NUM_BLOCKS][MAX_BLOCK_SIZE];
        for (int i = 0; i < NUM_BLOCKS; i++) {
            bufferBlocks[i][0] = new BuddyBuffer(MAX_BLOCK_SIZE * i, MAX_BLOCK_SIZE);
        }
    }

    /**
     * Determines if available space is tight (i.e. less than 2 full size blocks remaining)
     * @return true if tight
     */
    private boolean isTight(){
        int numEmptyBlocks = 0;
        BuddyBuffer temp = null;
        for (int i = 0; i < NUM_BLOCKS; i++) {
            temp = bufferBlocks[i][0];
            if (temp.getSize() == MAX_BLOCK_SIZE && !temp.isAssigned()) numEmptyBlocks++;
        }
        return numEmptyBlocks < 2;
    }

    /**
     * Requests a buffer if size "size"
     * Returns -1 if unable to find a suitable buffer
     * Returns -2 if size is greater than MAX_BLOCK_SIZE
     * @param size size of buffer needed
     * @return index of buffer found or error code
     */
    public int getBlock(int size) {

        if (size > MAX_BLOCK_SIZE-1) return -2; // Size requested is too big

        // Convert size to smallest buffer that can fit it
        for (int i = 3; i < 9; i++) {
            int temp = (int)Math.pow(2,i)-1;
            if (size <= temp){
                size = temp;
                break;
            }
        }

        // Find buffer that works
        BuddyBuffer bufferToReturn = null;
        for (int i = 0; i < NUM_BLOCKS && bufferToReturn == null; i++) {
            BuddyBuffer temp = bufferBlocks[i][0]; // Retrieve first buffer in block
            while (temp != null && bufferToReturn == null){ // Haven't reached end of block or found a buffer to return
                if (temp.isAssigned() || temp.getSize() < size){ // current buffer is assigned or too small
                    temp = temp.getNextBuffer();
                }
                else { // found a buffer that either works or can be broken down
                    while (temp.getSize() / 2 > size && splitBuffer(temp.getIndex())){ /* splits buffer until it is small enough */ }
                    bufferToReturn = temp;
                    try {
                        bufferToReturn.assign();
                    } catch (InvalidStateException e) {
                        e.printStackTrace();
                        System.exit(0);
                    }
                }
            }
        }

        // If bufferToReturn has been populated, return index of empty slot following control block, else return -1
        return bufferToReturn != null ? bufferToReturn.getIndex()+1 : -1;
    }

    /**
     * Splits the buffer at index "index" in two
     * @param index index of the buffer to be split
     * @return true if able to split
     */
    private boolean splitBuffer(int index){
        BuddyBuffer bufferToSplit = findBuffer(index);
        int newSize = bufferToSplit.getSize() / 2;

        if (newSize < Math.pow(2,3)) return false;

        bufferToSplit.setSize(newSize);
        BuddyBuffer newBuffer = new BuddyBuffer(index + newSize, newSize);
        newBuffer.setNextBuffer(bufferToSplit.getNextBuffer());
        newBuffer.setPrevBuffer(bufferToSplit);
        addBuffer(index + newSize, newBuffer);
        if (bufferToSplit.getNextBuffer() != null)bufferToSplit.getNextBuffer().setPrevBuffer(newBuffer);
        bufferToSplit.setNextBuffer(newBuffer);
        return true;
    }

    /**
     * Return and free up buffer at "index"
     * @param index index of buffer to be returned
     */
    public void returnBuffer(int index){
        index--;
        BuddyBuffer returnedBuffer = findBuffer(index);
        try {
            returnedBuffer.unassign();
        }

        catch (InvalidStateException e) {
            e.printStackTrace();
            System.exit(0);
        }

        // Try to join buffers
        tryJoinBuffers(index);
    }

    /**
     * Try to join the buffer at "index" with its buddy
     * @param index index of buffer to try and join
     */
    private void tryJoinBuffers(int index){
        /*
        int block = index / MAX_BLOCK_SIZE;
        int relativeIndex = index % MAX_BLOCK_SIZE;*/
        BuddyBuffer bufferToJoin = findBuffer(index);//bufferBlocks[block][relativeIndex];

        while (true) {
            // Buffer passed is assigned
            if (bufferToJoin.isAssigned()) {
                return;
            }

            int i = ((bufferToJoin.getIndex() / bufferToJoin.getSize()) % 2);

            if (i == 0) { // buddy is next buffer
                if (bufferToJoin.getNextBuffer() == null || bufferToJoin.getNextBuffer().getSize() != bufferToJoin.getSize()
                        || bufferToJoin.getNextBuffer().isAssigned()) return; // Buffer's buddy is assigned
                joinBuffers(bufferToJoin);
            } else { // buddy is previous buffer
                if (bufferToJoin.getPrevBuffer() == null || bufferToJoin.getPrevBuffer().getSize() != bufferToJoin.getSize()
                        || bufferToJoin.getPrevBuffer().isAssigned()) return; // Buffer's buddy is assigned
                else {
                    bufferToJoin = bufferToJoin.getPrevBuffer();
                    joinBuffers(bufferToJoin);
                }
            }
        }
    }

    /**
     * Joins buffer that is passed with its buddy
     * @param buffer buffer to join. Should always be the first buddy
     */
    private void joinBuffers(BuddyBuffer buffer){
        BuddyBuffer bufferToDelete = buffer.getNextBuffer();
        BuddyBuffer newNextBuffer = bufferToDelete.getNextBuffer();
        bufferBlocks[bufferToDelete.getIndex()/MAX_BLOCK_SIZE][bufferToDelete.getIndex()%MAX_BLOCK_SIZE] = null;
        buffer.setNextBuffer(newNextBuffer);
        if (newNextBuffer != null)newNextBuffer.setPrevBuffer(buffer);
        buffer.setSize(buffer.getSize()*2);
    }

    /**
     * Returns the buffer at index "index"
     * @param index index of buffer to find
     * @return Buffer at index
     */
    private BuddyBuffer findBuffer(int index){
        int block = index / MAX_BLOCK_SIZE;
        int relativeIndex = index % MAX_BLOCK_SIZE;
        return bufferBlocks[block][relativeIndex];
    }

    /**
     * Puts a buffer in bufferBlock at "index"
     * @param index index at which to place buffer
     * @param buffer buffer to add to bufferBlock
     */
    private void addBuffer(int index, BuddyBuffer buffer){
        int block = index / MAX_BLOCK_SIZE;
        int relativeIndex = index % MAX_BLOCK_SIZE;
        bufferBlocks[block][relativeIndex] = buffer;
    }

    HashMap<Integer, Integer> freeBuffers(){
        HashMap<Integer, Integer> freeBufferMap = new HashMap<>();
        BuddyBuffer temp = null;
        for (int i = 0; i < NUM_BLOCKS; i++){
            temp = bufferBlocks[i][0];
            while (temp != null){
                if (temp.isAssigned()){
                    temp = temp.getNextBuffer();
                    continue;
                }
                if (freeBufferMap.containsKey(temp.getSize())){
                    int n = freeBufferMap.get(temp.getSize());
                    freeBufferMap.put(temp.getSize(), n + 1);
                }
                else freeBufferMap.put(temp.getSize(), 1);
                temp = temp.getNextBuffer();
            }
        }
        return freeBufferMap;
    }

    private String createReport(){
        HashMap<Integer, Integer> freeBuffers = freeBuffers();

        StringBuilder builder = new StringBuilder();

        builder.append("Report:\n");
        builder.append(String.format("\tIs tight: %s\n", isTight()?"yes":"no"));

        Integer[] keyset = freeBuffers.keySet().toArray(new Integer[]{});
        if (keyset.length > 0) {
            Arrays.sort(keyset);
            builder.append("\tFree buffers:\n");
            for (Integer i : keyset) {
                builder.append(String.format("\t - size %3d: %3d\n", i - 1, freeBuffers.get(i)));
            }
        }
        else builder.append("\tNo free buffers left.\n");

        return builder.toString();
    }

    private static void printMessage(String message, BufferedWriter writer) throws IOException {
        System.out.println(message);
        writer.write(message);
        writer.newLine();
    }

    private class BuddyBuffer {
        private BuddyBuffer nextBuffer;
        private BuddyBuffer prevBuffer;
        private int index;
        private int size;
        private boolean assigned;

        private void init(int index, int size, BuddyBuffer nextBuffer, BuddyBuffer prevBuffer){
            this.index = index;
            this.size = size;
            this.nextBuffer = nextBuffer;
            this.prevBuffer = prevBuffer;
            assigned = false;
        }

        private BuddyBuffer(){ /* don't allow instantiation with no info */ };

        /**
         * Builds buddy buffer with next and previous set to void
         * @param index index of the buffer
         * @param size size of the buffer
         */
        public BuddyBuffer(int index, int size){
            init(index, size, null, null);
        }

        /**
         * Builds buffer with next and previous buffers assigned
         * @param index index of buffer
         * @param size size of buffer
         * @param nextBuffer next buffer in array
         * @param prevBuffer previous buffer in array
         */
        public BuddyBuffer(int index, int size, BuddyBuffer nextBuffer, BuddyBuffer prevBuffer){
            init(index, size, nextBuffer, prevBuffer);
        }

        // Public setters
        public void setSize(int newSize) { size = newSize; }
        public void setNextBuffer(BuddyBuffer nextBuffer) { this.nextBuffer = nextBuffer; }
        public void setPrevBuffer(BuddyBuffer prevBuffer) { this.prevBuffer = prevBuffer; }
        public void assign() throws InvalidStateException {
            if (assigned) throw new InvalidStateException("Buffer already assigned.");
            assigned = true;
        }
        public void unassign() throws InvalidStateException{
            if(!assigned) throw new InvalidStateException("Buffer already unassigned.");
            assigned = false;
        }

        // Public getters
        public int getIndex() { return index; }
        public int getSize() { return size; }
        public BuddyBuffer getNextBuffer() { return nextBuffer; }
        public BuddyBuffer getPrevBuffer() { return prevBuffer; }
        public boolean isAssigned() { return assigned; }

        @Override
        public String toString(){
            return String.format("Buffer of size %d at index %d, is%s assigned", size, index, assigned?"":" not");
        }
    }

    private class InvalidStateException extends Exception {
        public InvalidStateException(String message){
            super(message);
        }
        public InvalidStateException(){}
    }

}
