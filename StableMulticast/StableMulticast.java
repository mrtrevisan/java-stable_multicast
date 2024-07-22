package StableMulticast;
import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class StableMulticast {
    private String ip;
    private Integer port;

    private IStableMulticast client;
    private List<InetSocketAddress> groupMembers;
    private DatagramSocket socket;
    private MulticastSocket multicastSocket;
    private InetAddress groupAddress;

    private Thread discoveryThread;
    
    private Map<String, Boolean> messageBuffer;
    private Map<String, int[]> vectorClocks; // Para armazenar os relógios vetoriais
    private int[] localVectorClock;

    @SuppressWarnings("deprecation")
    public StableMulticast(String ip, Integer port, IStableMulticast client) throws IOException {
        this.ip = ip;
        this.port = port;
        this.client = client;
        this.groupMembers = Collections.synchronizedList(new ArrayList<>());
        this.messageBuffer = new ConcurrentHashMap<>();
        this.vectorClocks = new ConcurrentHashMap<>();
        
        this.localVectorClock = new int[10]; // Supondo um máximo de 10 processos no grupo
        
        this.socket = new DatagramSocket();
        this.multicastSocket = new MulticastSocket(this.port);
        this.groupAddress = InetAddress.getByName(this.ip);

       
        System.out.println("Joining multicast group: " + groupAddress + " on port: " + port);
        multicastSocket.joinGroup(groupAddress);
        System.out.println("Joined multicast group successfully.");

        startDiscoveryService();
        startMessageReceiver();
    }

    private void startDiscoveryService() {
        discoveryThread = new Thread(() -> {
            try {
                while (true) {
                    byte[] buf = new byte[256];
                    DatagramPacket packet = new DatagramPacket(buf, buf.length);

                    // aguarda por mensagens de usuários entrando
                    System.out.println("Waiting to receive a packet...");
                    multicastSocket.receive(packet); // Bloqueia até que um pacote seja recebido
                    System.out.println("Packet received!");
                    InetSocketAddress address = new InetSocketAddress(packet.getAddress(), packet.getPort());

                    // adiciona o usuário aos membros do grupo, caso ele não esteja lá
                    if (!groupMembers.contains(address)) {
                        System.out.println("address adicionado:" + address);
                        groupMembers.add(address);
                    } else {
                        System.out.println("Address already in group: " + address);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        discoveryThread.start();
    }

    private void startMessageReceiver() {
        new Thread(() -> {
            byte[] buf = new byte[256];

            while (true) {
                try {
                    DatagramPacket packet = new DatagramPacket(buf, buf.length);
                    socket.receive(packet);
                    
                    String received = new String(packet.getData(), 0, packet.getLength());
                    processReceivedMessage(received);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private void processReceivedMessage(String message) {
        // Verifica se a mensagem é nova e deve ser entregue
        if (!messageBuffer.containsKey(message)) {
            messageBuffer.put(message, false);
            client.deliver(message);
            updateVectorClocks(message);
            messageBuffer.put(message, true);
        }
        printStatus();
    }

    private void updateVectorClocks(String message) {
        // Atualiza os relógios vetoriais com base na mensagem recebida
        // (Simulação simples para demonstrar a atualização dos relógios vetoriais)
        int senderId = Integer.parseInt(message.split(":")[0]); // Supondo que o ID do remetente está no início da mensagem
        localVectorClock[senderId]++;
        vectorClocks.put(message, localVectorClock.clone());
        
    }

    // private void startUserInterface() {
    //     new Thread(() -> {
    //         try (Scanner scanner = new Scanner(System.in)) {
    //             while (true) {
    //                 System.out.print("Digite a mensagem para enviar: ");
    //                 String msg = scanner.nextLine();
    //                 System.out.print("Enviar a todos os membros do grupo? (s/n): ");
    //                 String sendToAll = scanner.nextLine();
    //                 if (sendToAll.equalsIgnoreCase("s")) {
    //                     msend(msg, client);
    //                 } else {
    //                     for (InetSocketAddress member : groupMembers) {
    //                         System.out.print("Enviar para " + member + "? (s/n): ");
    //                         String sendToMember = scanner.nextLine();
    //                         if (sendToMember.equalsIgnoreCase("s")) {
    //                             sendUnicast(msg, member);
    //                         }
    //                     }
    //                 }
    //             }
    //         }
    //     }).start();
    // }

    public void sendUnicast(String msg, InetSocketAddress member) {
        try {
            byte[] buf = msg.getBytes();
            DatagramPacket packet = new DatagramPacket(buf, buf.length, member.getAddress(), member.getPort());
            socket.send(packet);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void msend(String msg, IStableMulticast client) {
        try {
            localVectorClock[0]++; // Incrementa o relógio vetorial local
            byte[] buf = msg.getBytes();
            for (InetSocketAddress member : groupMembers) {
                DatagramPacket packet = new DatagramPacket(buf, buf.length, member.getAddress(), member.getPort());
                socket.send(packet);
            }
            vectorClocks.put(msg, localVectorClock.clone());
        } catch (IOException e) {
            e.printStackTrace();
        }
        printStatus();
    }

    private void printStatus() {
        System.out.println("Buffer de Mensagens: " + messageBuffer);
        System.out.println("Relógios Lógicos: " + Arrays.toString(localVectorClock));
    }

    public List<InetSocketAddress> getGroupMembers() {
        return Collections.unmodifiableList(groupMembers);
    }
}
