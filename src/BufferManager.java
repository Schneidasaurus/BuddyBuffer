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
        int size = bufferToSplit.getSize() / 2;

        if (size < Math.pow(2,3)) return false;

        bufferToSplit.setSize(size);
        BuddyBuffer newBuffer = new BuddyBuffer(index + size, size, bufferToSplit.getNextBuffer(), bufferToSplit);
        bufferBlocks[block][relativeIndex+size] = newBuffer;
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
                if (bufferToJoin.getNextBuffer() == null || bufferToJoin.getNextBuffer().isAssigned()) return; // Buffer's buddy is assigned
                joinBuffers(bufferToJoin);
            } else { // buddy is previous buffer
                if (bufferToJoin.getPrevBuffer() == null || bufferToJoin.getPrevBuffer().isAssigned()) return; // Buffer's buddy is assigned
                else {
                    bufferToJoin = bufferToJoin.getPrevBuffer();
                    joinBuffers(bufferToJoin);
                }
            }
        }
    }

    private void joinBuffers(BuddyBuffer buffer){
        buffer.setNextBuffer(buffer.getNextBuffer().getNextBuffer());
        bufferBlocks[buffer.getIndex()/MAX_BLOCK_SIZE][buffer.getIndex()+buffer.getSize()] = null;
        if (buffer.getNextBuffer() != null)buffer.getNextBuffer().setPrevBuffer(buffer);
        buffer.setSize(buffer.getSize()*2);
    }

    private String createReport(){
        HashMap<Integer, Integer> map = new HashMap<>();
        BuddyBuffer temp = null;
        for (int i = 0; i < NUM_BLOCKS; i++) {
            temp = bufferBlocks[i][0];
            while (temp != null){
                if (temp.isAssigned()) {
                    temp = temp.getNextBuffer();
                    continue;
                }
                if (map.containsKey(temp.getSize())){
                    int n = map.get(temp.getSize());
                    map.put(temp.getSize(), n + 1);
                }
                else map.put(temp.getSize(), 1);
                temp = temp.getNextBuffer();
            }
        }

        StringBuilder builder = new StringBuilder();

        builder.append("Report:\n");
        builder.append(String.format("\tIs tight: %s\n", isTight()?"yes":"no"));

        Integer[] keyset = map.keySet().toArray(new Integer[]{});
        if (keyset.length > 0) {
            Arrays.sort(keyset);
            for (Integer i : keyset) {
                builder.append(String.format("\tBuffers of size %5d: %5d\n", i - 1, map.get(i)));
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
}
