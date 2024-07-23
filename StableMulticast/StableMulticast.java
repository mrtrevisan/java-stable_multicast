package StableMulticast;
import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@SuppressWarnings("deprecation")
public class StableMulticast {
    private String ip;
    private Integer port;
    private IStableMulticast client;
    private String localId;
    
    private List<InetSocketAddress> groupMembers;
    private DatagramSocket socket;

    private InetAddress groupAddress;

    private Thread discoveryThread;
    
    private Map<String, Boolean> messageBuffer;
    private Map<String, int[]> vectorClocks; // Para armazenar os relógios vetoriais
    private int[] localVectorClock;

    private String multicastIp = "230.0.0.0";
    private int multicastPort = 4446;

    public StableMulticast(String ip, Integer port, IStableMulticast client) throws IOException {
        this.ip = ip;
        this.port = port;
        this.client = client;
        this.groupMembers = Collections.synchronizedList(new ArrayList<>());
        this.localId = InetAddress.getLocalHost().getHostName() + ":" + port;


        this.messageBuffer = new ConcurrentHashMap<>();
        this.vectorClocks = new ConcurrentHashMap<>();
        
        this.localVectorClock = new int[10]; // Supondo um máximo de 10 processos no grupo
        
        this.socket = new DatagramSocket(port);
        this.groupAddress = InetAddress.getByName(this.ip);

        startDiscoveryService();
        sendHello();
        startMessageReceiver();
    }

    private void startDiscoveryService() {
        try {
            new Thread(() -> {
                try (MulticastSocket multicastSocket = new MulticastSocket(multicastPort);){
                    InetAddress group = InetAddress.getByName(multicastIp);
                    multicastSocket.joinGroup(group);

                    while (true) {
                        byte[] buf = new byte[256];
                        DatagramPacket packet = new DatagramPacket(buf, buf.length);
                        multicastSocket.receive(packet);

                        String received = new String(packet.getData(), 0, packet.getLength());
                        String[] parts = received.split(":");

                        if (parts[0].equals("SYSTEMHello") && (!(parts[1]+ ":" +parts[2]).equals(localId))) {
                            // System.out.println(parts[0] + "///" + parts[1] + "///" + parts[2] + "///" + localId);
                            String host = parts[1];
                            int port = Integer.parseInt(parts[2]);
                            
                            InetSocketAddress TempMember = new InetSocketAddress(InetAddress.getByName(host), port);
                            // Adiciona o membro ao grupo, caso ele não esteja lá
                            if (!groupMembers.contains(TempMember)) {
                                groupMembers.add(TempMember);
                                System.out.println("Membro Descoberto: " + TempMember);
                            }
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }).start();
        } catch (Exception e) {
            e.printStackTrace();
        }


        // discoveryThread = new Thread(() -> {
        //     try {
        //         while (true) {
        //             byte[] buf = new byte[256];
        //             DatagramPacket packet = new DatagramPacket(buf, buf.length);

        //             // aguarda por mensagens de usuários entrando
        //             System.out.println("Waiting to receive a packet...");
        //             multicastSocket.receive(packet); // Bloqueia até que um pacote seja recebido
        //             System.out.println("Packet received!");
        //             InetSocketAddress address = new InetSocketAddress(packet.getAddress(), packet.getPort());

        //             // adiciona o usuário aos membros do grupo, caso ele não esteja lá
        //             if (!groupMembers.contains(address)) {
        //                 System.out.println("address adicionado:" + address);
        //                 groupMembers.add(address);
        //             } else {
        //                 System.out.println("Address already in group: " + address);
        //             }
        //         }
        //     } catch (IOException e) {
        //         e.printStackTrace();
        //     }
        // });

        // discoveryThread.start();
    }

    private void sendHello()
    {
        new Thread( () -> {
            try ( MulticastSocket multicastSocket = new MulticastSocket(multicastPort);) {
                InetAddress group = InetAddress.getByName(multicastIp);
                multicastSocket.joinGroup(group);
                
                String msg = "SYSTEMHello:"+ localId;
                byte[] data = msg.getBytes();
                DatagramPacket hello = new DatagramPacket(data, data.length, group, multicastPort);
                
                while (true) {
                    multicastSocket.send(hello);
                    Thread.sleep(2000);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
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
            // updateVectorClocks(message);
            messageBuffer.put(message, true);
        }
        //printStatus();
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
            String msg_ = msg + "@" + localId;

            byte[] buf = msg_.getBytes();
            DatagramPacket packet = new DatagramPacket(buf, buf.length, member.getAddress(), member.getPort());
            socket.send(packet);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void sendMulticast(String msg) {
        try {
            String msg_ = msg + "@" + localId;

            byte[] buf = msg_.getBytes();
            for (InetSocketAddress member : groupMembers) {
                DatagramPacket packet = new DatagramPacket(buf, buf.length, member.getAddress(), member.getPort());
                socket.send(packet);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    public void msend(String msg, IStableMulticast client) {
        try{
            // localVectorClock[0]++; // Incrementa o relógio vetorial local
            Scanner scanner = new Scanner(System.in);
            System.out.println("Enviar a mensagem em Mulicast ? (S/N)");
            String confirm = scanner.nextLine();

            if (confirm.equalsIgnoreCase("s")) {
                // multicast para todos os membros do grupo
                sendMulticast(msg);
            } else {
                // unicast para cada membro do grupo
                for (InetSocketAddress member : this.getGroupMembers()) {
                    System.out.print("Enviar para " + member + "? (s/n): ");
                    String sendToMember = scanner.nextLine();

                    if (sendToMember.equalsIgnoreCase("s")) {
                        this.sendUnicast(msg, member);
                    }
                }
            }
            // vectorClocks.put(msg, localVectorClock.clone());
        } catch (Exception e) {
            e.printStackTrace();
        }
        // printStatus();

        // Message message = new Message(msg, Arrays.copyOf(localClock, localClock.length), localId);
        // localClock[getLocalIndex()]++;

    }

    private void printStatus() {
        System.out.println("Buffer de Mensagens: " + messageBuffer);
        System.out.println("Relógios Lógicos: " + Arrays.toString(localVectorClock));
    }

    public List<InetSocketAddress> getGroupMembers() {
        return Collections.unmodifiableList(groupMembers);
    }
}
