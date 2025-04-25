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

                // Packet synAck = new Packet(0, expectedSeq, System.nanoTime(), true, false, true, new byte[0]);
                // sendPacket(pkt);
                // log("snd", synAck, "SA");

                Packet synAck = new Packet(0, expectedSeq, System.nanoTime(), true, false, true, new byte[0]);
                sendPacket(synAck, lastReceived);  // send syn ack
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

            if (!pkt.isValidChecksum(pkt.encode())) {
                // bad checksum
                continue;
            }

            if (pkt.FIN) {
                log("rcv", pkt, "F");
                if (pkt.seq == expectedSeq) {
                    expectedSeq = pkt.seq + 1;
                    break;
                } else {
                    // FIN arrived but missing data, ignore
                    System.out.println("FIN received early, missing data, ignoring...");
                    continue;
                }
            }

            if (pkt.seq == expectedSeq) {
                fileOutput.write(pkt.data);
                expectedSeq += pkt.data.length;
                log("rcv", pkt, "AD");
            } else {
                // out of order packet — discard, but ACK expectedSeq
                log("rcv", pkt, "AD (out-of-order)");
                // Packet dupAck = new Packet(0, expectedSeq, pkt.timestamp, false, false, true, new byte[0]);
                // sendPacket(dupAck, lastReceived);
                // log("snd", dupAck, "A (dup)");
            }

            Packet ack = new Packet(0, expectedSeq, pkt.timestamp, false, false, true, new byte[0]);
            // sendPacket(pkt);
            sendPacket(ack, lastReceived);
            log("snd", ack, "A");
        }
    }

    private void terminate() throws IOException {
        if (lastReceived != null) {
            Packet finAck = new Packet(0, expectedSeq, System.nanoTime(), false, true, true, new byte[0]);
            // sendPacket(null);
            sendPacket(finAck, lastReceived);
            log("snd", finAck, "AF");

            Packet finalAck = receivePacket();
            if (finalAck.ACK) {
                log("rcv", finalAck, "A");
            }
        }

        fileOutput.close();
    }

    // private Packet receivePacket() throws IOException {
    //     byte[] buf = new byte[mtu + 100];
    //     DatagramPacket dp = new DatagramPacket(buf, buf.length);
    //     socket.receive(dp);
    //     return Packet.decode(dp.getData());
    // }

    // private void sendPacket(Packet pkt, Packet original) throws IOException {
    //     InetAddress addr = (original != null) ? InetAddress.getByName("localhost") : InetAddress.getLocalHost();
    //     int port = (original != null) ? original.encode().length : socket.getLocalPort();
    //     byte[] raw = pkt.encode();
    //     socket.send(new DatagramPacket(raw, raw.length, addr, port));
    // }

    private DatagramPacket lastReceived;

    // private Packet receivePacket() throws IOException {
    //     byte[] buf = new byte[mtu + 100];
    //     lastReceived = new DatagramPacket(buf, buf.length);
    //     socket.receive(lastReceived);
    //     return Packet.decode(lastReceived.getData());
    // }

    private Packet receivePacket() throws IOException {
        byte[] buf = new byte[mtu + 100];
        DatagramPacket dp = new DatagramPacket(buf, buf.length);
        socket.receive(dp);
        lastReceived = dp;  // store last received packet to reply to
        return Packet.decode(dp.getData());
    }

    // private void sendPacket(Packet pkt) throws IOException {
    //     if (lastReceived == null) return;

    //     InetAddress addr = lastReceived.getAddress();
    //     int port = lastReceived.getPort();  // THIS is key
    //     byte[] raw = pkt.encode();
    //     socket.send(new DatagramPacket(raw, raw.length, addr, port));
    // }
    

    private void sendPacket(Packet pkt, DatagramPacket received) throws IOException {
        byte[] raw = pkt.encode();
        InetAddress addr = received.getAddress();
        int port = received.getPort();
        socket.send(new DatagramPacket(raw, raw.length, addr, port));
    }

    private void log(String dir, Packet pkt, String flags) {
        System.out.printf("%s %.3f %s %d %d %d%n", dir, Utils.now(), flags, pkt.seq, pkt.data.length, pkt.ack);
    }
} 

