import java.io.*;
import java.net.*;
import java.util.*;

public class Sender {
    private final DatagramSocket socket;
    private final InetAddress receiverAddr;
    private final int receiverPort;
    private final FileInputStream fileInput;
    private final int mtu;
    private final int sws;
    private int seq = 0;
    private final Map<Integer, Packet> unackedPackets = new LinkedHashMap<>();
    private final Map<Integer, Long> sendTimestamps = new HashMap<>();
    private long timeout;
    private boolean firstAck = true;

    public Sender(String ip, int port, File file, int mtu, int sws, int localPort) throws Exception {
        this.receiverAddr = InetAddress.getByName(ip);
        this.receiverPort = port;
        this.fileInput = new FileInputStream(file);
        this.mtu = mtu;
        this.sws = sws;
        this.socket = new DatagramSocket(localPort);
        this.timeout = Utils.initialTimeout();
    }

    public void start() throws IOException {
        handshake();
        seq = sendData(); // update global seq
        terminate();
    }
    

    private void handshake() throws IOException {
        Packet syn = new Packet(seq, 0, System.nanoTime(), true, false, false, new byte[0]);
        sendPacket(syn);
        log("snd", syn, "S");

        System.out.println("waiting for SYN-ACK"); // this is where we are stuck !! 

        while (true) {
            Packet resp = receivePacket();
            
            System.out.println("recoeved packet: flags = " + resp.SYN + "," + resp.ACK);

            if (resp.SYN && resp.ACK) {
                log("rcv", resp, "SA");
                Packet ack = new Packet(++seq, resp.seq + 1, System.nanoTime(), false, false, true, new byte[0]);
                sendPacket(ack);
                log("snd", ack, "A");
                break;
            }
        }
    }

    private int sendData() throws IOException {
        int lastAck = 0;
        int dupAckCount = 0;
        byte[] buffer = new byte[mtu];
        int read = fileInput.read(buffer);
        int base = seq;
        int nextSeq = seq;
    
        while (read != -1 || !unackedPackets.isEmpty()) {
            // Send as much as window allows
            while ((nextSeq - base) < sws && read != -1) {
                byte[] data = Arrays.copyOf(buffer, read);
                Packet pkt = new Packet(nextSeq, 0, System.nanoTime(), false, false, true, data);
                unackedPackets.put(nextSeq, pkt);
                sendTimestamps.put(nextSeq, pkt.timestamp);
                sendPacket(pkt);
                log("snd", pkt, "AD");
    
                nextSeq += data.length;
                read = fileInput.read(buffer);
            }
    
            socket.setSoTimeout((int) (timeout / 1_000_000));
            try {
                Packet ackPkt = receivePacket();
                if (!ackPkt.isValidChecksum(ackPkt.encode())) continue;
    
                log("rcv", ackPkt, "A");
                int ackNum = ackPkt.ack;
    
                if (ackNum == lastAck) {
                    dupAckCount++;
                    if (dupAckCount == 3) {
                        // Fast retransmit
                        Packet toResend = null;
                        for (Map.Entry<Integer, Packet> entry : unackedPackets.entrySet()) {
                            if (entry.getKey() < ackNum) {
                                toResend = entry.getValue();
                                break;
                            }
                        }
                        if (toResend != null) {
                            sendPacket(toResend);
                            log("snd", toResend, "AD (fast retransmit)");
                        }
                    }
                } else {
                    dupAckCount = 1;
                    lastAck = ackNum;
                }
    
                // Remove acknowledged packets
                Iterator<Map.Entry<Integer, Packet>> iter = unackedPackets.entrySet().iterator();
                while (iter.hasNext()) {
                    Map.Entry<Integer, Packet> entry = iter.next();
                    if (entry.getKey() + entry.getValue().data.length <= ackNum) {
                        iter.remove();
                    }
                }
                base = ackNum;
    
            } catch (SocketTimeoutException e) {
                if (!unackedPackets.isEmpty()) {
                    Packet pkt = unackedPackets.values().iterator().next();
                    sendPacket(pkt);
                    log("snd", pkt, "AD (timeout resend)");
                }
            }
        }
    
        return nextSeq;  // Return final sequence number
    }
    

    private void terminate() throws IOException {
        Packet fin = new Packet(seq, 0, System.nanoTime(), false, true, false, new byte[0]);
        sendPacket(fin);
        log("snd", fin, "F");

        while (true) {
            Packet resp = receivePacket();
            if (resp.FIN && resp.ACK) {
                log("rcv", resp, "AF");
                Packet finalAck = new Packet(++seq, resp.seq + 1, System.nanoTime(), false, false, true, new byte[0]);
                sendPacket(finalAck);
                log("snd", finalAck, "A");
                break;
            }
        }
    }

    private void sendPacket(Packet pkt) throws IOException {
        byte[] raw = pkt.encode();
        socket.send(new DatagramPacket(raw, raw.length, receiverAddr, receiverPort));
    }

    private Packet receivePacket() throws IOException {
        byte[] buf = new byte[mtu + 100]; // enough for headers
        DatagramPacket dp = new DatagramPacket(buf, buf.length);
        socket.receive(dp);

        System.out.println("raw packet received on h1: length=" + dp.getLength());
        
        return Packet.decode(dp.getData());
    }

    private void log(String dir, Packet pkt, String flags) {
        System.out.printf("%s %.3f %s %d %d %d%n", dir, Utils.now(), flags, pkt.seq, pkt.data.length, pkt.ack);
    }
}
