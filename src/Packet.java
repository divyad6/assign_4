import java.nio.ByteBuffer;
import java.util.Arrays;

public class Packet {
    public static final int HEADER_SIZE = 24;
    public int seq, ack;
    public long timestamp;
    public int length; // last 3 bits for flags
    public boolean SYN, FIN, ACK;
    public short checksum;
    public byte[] data;

    public Packet(int seq, int ack, long timestamp, boolean SYN, boolean FIN, boolean ACK, byte[] data) {
        this.seq = seq;
        this.ack = ack;
        this.timestamp = timestamp;
        this.SYN = SYN;
        this.FIN = FIN;
        this.ACK = ACK;
        this.data = data;
        this.length = data.length;
        setFlags();
        this.checksum = 0;
        this.checksum = computeChecksum(encode());
    }

    private void setFlags() {
        int flags = 0;
        if (SYN) flags |= 1 << 2;
        if (FIN) flags |= 1 << 1;
        if (ACK) flags |= 1;
        this.length = (length << 3) | flags;
    }

    public byte[] encode() {
        ByteBuffer buffer = ByteBuffer.allocate(HEADER_SIZE + data.length);
        buffer.putInt(seq);
        buffer.putInt(ack);
        buffer.putLong(timestamp);
        buffer.putInt(length);
        buffer.putShort((short) 0); // placeholder
        buffer.putShort((short) 0); // padding
        buffer.put(data);
        byte[] pkt = buffer.array();
        short cs = computeChecksum(pkt);
        buffer.putShort(20, cs);
        return buffer.array();
    }

    public static Packet decode(byte[] raw) {
        ByteBuffer buffer = ByteBuffer.wrap(raw);
        int seq = buffer.getInt();
        int ack = buffer.getInt();
        long timestamp = buffer.getLong();
        int lenFlags = buffer.getInt();
        short checksum = buffer.getShort();
        buffer.getShort(); // padding

        int dataLen = lenFlags >>> 3;
        int flags = lenFlags & 0x7;

        byte[] data = new byte[dataLen];
        buffer.get(data);

        Packet p = new Packet(seq, ack, timestamp, (flags & 0x4) > 0, (flags & 0x2) > 0, (flags & 0x1) > 0, data);
        p.checksum = checksum;
        return p;
    }

    public static short computeChecksum(byte[] data) {
        int sum = 0;
        for (int i = 0; i < data.length; i += 2) {
            int word = (data[i] & 0xFF) << 8;
            if (i + 1 < data.length)
                word |= (data[i + 1] & 0xFF);
            sum += word;
            if ((sum & 0xFFFF0000) != 0)
                sum = (sum & 0xFFFF) + 1;
        }
        return (short) ~sum;
    }

    public boolean isValidChecksum(byte[] raw) {
        return computeChecksum(raw) == 0;
    }
}
