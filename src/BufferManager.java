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

    private boolean isTight(){
        int numEmptyBlocks = 0;
        BuddyBuffer temp = null;
        for (int i = 0; i < NUM_BLOCKS; i++) {
            temp = bufferBlocks[i][0];
            if (temp.getSize() == MAX_BLOCK_SIZE && !temp.isAssigned()) numEmptyBlocks++;
        }
        return numEmptyBlocks < 2;
    }

    public int getBlock(int size) {
        // Size requested is too big
        if (size > MAX_BLOCK_SIZE-1) return -2;

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
            BuddyBuffer temp = bufferBlocks[i][0];
            while (temp != null && bufferToReturn == null){
                if (temp.isAssigned() || temp.getSize() < size){ // current buffer is assigned or too small
                    temp = temp.getNextBuffer();
                }
                else {
                    while (temp.getSize() / 2 > size && splitBuffer(temp.getIndex())){ }
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

    private boolean splitBuffer(int index){
        int block = index / MAX_BLOCK_SIZE;
        int relativeIndex = index % MAX_BLOCK_SIZE;
        BuddyBuffer bufferToSplit = bufferBlocks[block][relativeIndex];
        int newSize = bufferToSplit.getSize() / 2;

        if (newSize < Math.pow(2,3)) return false;

        bufferToSplit.setSize(newSize);
        BuddyBuffer newBuffer = new BuddyBuffer(index + newSize, newSize);
        newBuffer.setNextBuffer(bufferToSplit.getNextBuffer());
        newBuffer.setPrevBuffer(bufferToSplit);
        bufferBlocks[block][relativeIndex+newSize] = newBuffer;
        if (bufferToSplit.getNextBuffer() != null)bufferToSplit.getNextBuffer().setPrevBuffer(newBuffer);
        bufferToSplit.setNextBuffer(newBuffer);
        return true;
    }

    public void returnBuffer(int index){
        index--;
        int block = index / MAX_BLOCK_SIZE;
        int relativeIndex = index % MAX_BLOCK_SIZE;
        BuddyBuffer returnedBuffer = bufferBlocks[block][relativeIndex];
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

    private void tryJoinBuffers(int index){
        int block = index / MAX_BLOCK_SIZE;
        int relativeIndex = index % MAX_BLOCK_SIZE;
        BuddyBuffer bufferToJoin = bufferBlocks[block][relativeIndex];

        while (true) {
            // Buffer passed is assigned
            if (bufferToJoin.isAssigned()) {
                return;
            }

            int i = ((bufferToJoin.getIndex() / bufferToJoin.getSize()) % 2);

            if (i == 0) { // buddy is next buffer
                if (bufferToJoin.getNextBuffer() == null || bufferToJoin.getNextBuffer().getSize() != bufferToJoin.getSize() || bufferToJoin.getNextBuffer().isAssigned()) return; // Buffer's buddy is assigned
                joinBuffers(bufferToJoin);
            } else { // buddy is previous buffer
                if (bufferToJoin.getPrevBuffer() == null || bufferToJoin.getPrevBuffer().getSize() != bufferToJoin.getSize()|| bufferToJoin.getPrevBuffer().isAssigned()) return; // Buffer's buddy is assigned
                else {
                    bufferToJoin = bufferToJoin.getPrevBuffer();
                    joinBuffers(bufferToJoin);
                }
            }
        }
    }

    private void joinBuffers(BuddyBuffer buffer){
        BuddyBuffer bufferToDelete = buffer.getNextBuffer();
        BuddyBuffer newNextBuffer = bufferToDelete.getNextBuffer();
        bufferBlocks[bufferToDelete.getIndex()/MAX_BLOCK_SIZE][bufferToDelete.getIndex()%MAX_BLOCK_SIZE] = null;
        buffer.setNextBuffer(newNextBuffer);
        if (newNextBuffer != null)newNextBuffer.setPrevBuffer(buffer);
        buffer.setSize(buffer.getSize()*2);
    }

    private String createReport(){
        HashMap<Integer, Integer> freeBuffers = new HashMap<>();
        BuddyBuffer temp = null;
        for (int i = 0; i < NUM_BLOCKS; i++) {
            temp = bufferBlocks[i][0];
            while (temp != null){
                if (temp.isAssigned()) {
                    temp = temp.getNextBuffer();
                    continue;
                }
                if (freeBuffers.containsKey(temp.getSize())){
                    int n = freeBuffers.get(temp.getSize());
                    freeBuffers.put(temp.getSize(), n + 1);
                }
                else freeBuffers.put(temp.getSize(), 1);
                temp = temp.getNextBuffer();
            }
        }

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

        public BuddyBuffer(int index, int size){
            init(index, size, null, null);
        }

        /**
         *
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
