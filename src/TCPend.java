import java.io.File;

public class TCPend {
    public static void main(String[] args) throws Exception {
        String mode = null, remoteIP = null, filePath = null;
        int port = 0, remotePort = 0, mtu = 0, sws = 0;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-p": port = Integer.parseInt(args[++i]); break;
                case "-s": mode = "send"; remoteIP = args[++i]; break;
                case "-a": remotePort = Integer.parseInt(args[++i]); break;
                case "-f": filePath = args[++i]; break;
                case "-m": mtu = Integer.parseInt(args[++i]); break;
                case "-c": sws = Integer.parseInt(args[++i]); break;
            }
        }

        if (mode != null && mode.equals("send")) {
            Sender sender = new Sender(remoteIP, remotePort, new File(filePath), mtu, sws, port);
            sender.start();
        } else {
            Receiver receiver = new Receiver(port, new File(filePath), mtu, sws);
            receiver.start();
        }
    }
}
