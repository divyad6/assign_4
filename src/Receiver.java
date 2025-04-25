import java.io.*;
import java.net.*;
import java.util.*;

public class Receiver {
    private final DatagramSocket socket;
    private final FileOutputStream fileOutput;
    private final int mtu;
    private final int sws;
    private int expectedSeq = 0;

    public Receiver(int port, File file, int mtu, int sws) throws IOException {
        this.socket = new DatagramSocket(port);
        this.fileOutput = new FileOutputStream(file);
        this.mtu = mtu;
        this.sws = sws;
    }

    public void start() throws IOException {
        handshake();
        receiveData();
        terminate();
    }

    private void handshake() throws IOException {
        while (true) {
            Packet pkt = receivePacket();
            if (pkt.SYN) {
                log("rcv", pkt, "S");
                expectedSeq = pkt.seq + 1;

                Packet synAck = new Packet(0, expectedSeq, System.nanoTime(), true, false, true, new byte[0]);
                sendPacket(synAck, pkt);
                log("snd", synAck, "SA");
            } else if (pkt.ACK && !pkt.SYN && !pkt.FIN) {
                log("rcv", pkt, "A");
                break;
            }
        }
    }

    private void receiveData() throws IOException {
        while (true) {
            Packet pkt = receivePacket();
            if (pkt.FIN) {
                log("rcv", pkt, "F");
                expectedSeq = pkt.seq + 1;
                break;
            }

            if (!pkt.isValidChecksum(pkt.encode())) {
                // bad checksum
                continue;
            }

            if (pkt.seq == expectedSeq) {
                fileOutput.write(pkt.data);
                expectedSeq += pkt.data.length;
                log("rcv", pkt, "AD");
            } else {
                // out of order packet — discard, but ACK expectedSeq
                log("rcv", pkt, "AD (out-of-order)");
                Packet dupAck = new Packet(0, expectedSeq, pkt.timestamp, false, false, true, new byte[0]);
                sendPacket(dupAck, pkt);
                log("snd", dupAck, "A (dup)");
            }

            Packet ack = new Packet(0, expectedSeq, pkt.timestamp, false, false, true, new byte[0]);
            sendPacket(ack, pkt);
            log("snd", ack, "A");
        }
    }

    private void terminate() throws IOException {
        Packet finAck = new Packet(0, expectedSeq, System.nanoTime(), false, true, true, new byte[0]);
        sendPacket(finAck, null);
        log("snd", finAck, "AF");

        Packet finalAck = receivePacket();
        if (finalAck.ACK) {
            log("rcv", finalAck, "A");
        }

        fileOutput.close();
    }

    private Packet receivePacket() throws IOException {
        byte[] buf = new byte[mtu + 100];
        DatagramPacket dp = new DatagramPacket(buf, buf.length);
        socket.receive(dp);
        return Packet.decode(dp.getData());
    }

    private void sendPacket(Packet pkt, Packet original) throws IOException {
        InetAddress addr = (original != null) ? InetAddress.getByName("localhost") : InetAddress.getLocalHost();
        int port = (original != null) ? original.encode().length : socket.getLocalPort();
        byte[] raw = pkt.encode();
        socket.send(new DatagramPacket(raw, raw.length, addr, port));
    }

    private void log(String dir, Packet pkt, String flags) {
        System.out.printf("%s %.3f %s %d %d %d%n", dir, Utils.now(), flags, pkt.seq, pkt.data.length, pkt.ack);
    }
}
