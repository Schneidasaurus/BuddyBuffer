public class BuddyBuffer {
    private BuddyBuffer nextBuffer;
    private BuddyBuffer prevBuffer;
    private int index;
    private int size;
    private boolean assigned;

    private void init(int index, int size, BuddyBuffer nextBuffer, BuddyBuffer prevBuffer){
        this.index = index;
        this.size = size;
        this.nextBuffer = nextBuffer;
        this.prevBuffer = nextBuffer;
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
