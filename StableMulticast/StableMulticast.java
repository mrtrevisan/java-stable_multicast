package StableMulticast;
import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@SuppressWarnings("deprecation")
public class StableMulticast {
    private IStableMulticast client;
    private String localId;
    
    private List<InetSocketAddress> groupMembers;
    private DatagramSocket socket;
    
    private Map<String, List<String>> messageBuffer;
    private Map<String, Integer> localVectorClock;
    private Map<String, Map<String, Integer>> vectorClocks;

    private String multicastIp = "230.0.0.0";
    private int multicastPort = 4446;

    public StableMulticast(String ip, Integer port, IStableMulticast client) throws IOException {
        // cliente do middleware
        this.client = client;
        // membros do grupo multicast
        this.groupMembers = Collections.synchronizedList(new ArrayList<>());
        // id local do cliente, consiste em <nome do Host>:<porta> 
        this.localId = InetAddress.getLocalHost().getHostName() + ":" + port;
        // socket para envio/recebimento de mensagens
        this.socket = new DatagramSocket(port);
        // buffer de mensagens
        this.messageBuffer = new ConcurrentHashMap<>();
        // "matriz" de clocks, mapeia IDs para vetores
        this.vectorClocks = new ConcurrentHashMap<>();
        // "vetor" de clock local, mapeia IDs para valores de relógio
        this.localVectorClock = new ConcurrentHashMap<>();

        // atualiza os clocks na inicialização
        updateVectorClocks(localId, 0);
        System.out.println("Matriz de Relogios Logicos ao iniciar: \n" + stringMatrixClock());

        // inicializa o serviço de descoberta 
        startDiscoveryService();
        // envia mensagens de "hello" para o grupo
        sendHello();
        // inicializa o serviço receptor de mensagens
        startMessageReceiver();
    }

    // Serviço paralelo que escuta mensagens de "Hello"
    // e adiciona membros a lista de membros
    // mensagens de Hello começam com SYSTEMHello,
    // seguido do ID do processo que enviou 
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

                        // Se for uma msg de Hello e o autor não for o prórpio usuário
                        // adiciona a lista e notifca o usuário
                        // depois atualiza os relógios
                        if (parts[0].equals("SYSTEMHello") && (!(parts[1]+ ":" +parts[2]).equals(localId))) {
                            String host = parts[1];
                            int port = Integer.parseInt(parts[2]);
                            
                            InetSocketAddress TempMember = new InetSocketAddress(InetAddress.getByName(host), port);
                            // Adiciona o membro ao grupo, caso ele não esteja lá
                            // e atualiza o relógio vetorial
                            if (!groupMembers.contains(TempMember)) {
                                groupMembers.add(TempMember);
                                System.out.println("Membro Descoberto: " + TempMember);
                                updateVectorClocks(host+":"+port, -1);

                                System.out.println("Matriz de Relogios Logicos ao descobrir membro: \n" + stringMatrixClock());
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
    }
    
    // Envia um "Hello" para os membros do grupo a cada 2 segundos para detectar novos membros
    private void sendHello()
    {
        new Thread( () -> {
            try ( MulticastSocket multicastSocket = new MulticastSocket(multicastPort);) {
                InetAddress group = InetAddress.getByName(multicastIp);
                multicastSocket.joinGroup(group);
                // SYSTEMHello eh mensagem de sistema
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

    // Inicia uma thread para receber mensagens
    private void startMessageReceiver() {
        new Thread(() -> {
            byte[] buf = new byte[256];

            while (true) {
                try {
                    DatagramPacket packet = new DatagramPacket(buf, buf.length);
                    socket.receive(packet);
                    
                    String received = new String(packet.getData(), 0, packet.getLength());
                    // processa o recebimento da mensagem
                    processReceivedMessage(received);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private void processReceivedMessage(String message) {        
        String sender = message.split("@")[1];

        List<String> tempList;
        // verifica se o sender já enviou mensagens
        if (messageBuffer.containsKey(sender)) {
            tempList = messageBuffer.get(sender);
            tempList.add(message);
        }
        // caso contrário, cria uma nova lista para adicionar mensagens do sender
        else {
            tempList = new ArrayList<String>();
            tempList.add(message);
        }

        // adiciona a mensagem ao buffer
        messageBuffer.put(sender, tempList);
        // manda o relogio vetorial da mensagem para fazer as atualizacoes no relógio local e matriz
        getVectorClockFromMsg(message); 
        // controle de mensagens estáveis
        stabilizeMessages();
        // mostra o buffe de mensagens
        System.out.println("Buffer de Mensagens: " + stringMessageBuffer());
        // Entrega a mensagem ao cliente
        client.deliver(message.split("@")[0]);
    }

    // atualiza tanto o vetor local quanto o vetor de relogios
    private void updateVectorClocks(String id, int val) {
        this.localVectorClock.put(id, val);
        this.vectorClocks.put(localId, this.localVectorClock);
    }
    
    // retorna o valor de relógio de um processo
    private int getClockVal(String id) {
        return this.localVectorClock.get(id);
    }
    
    private String stringVectorClock() {
        StringJoiner sj = new StringJoiner(",");
        for (Map.Entry<String, Integer> entry : localVectorClock.entrySet()) {
            sj.add(entry.getKey() + ":" + entry.getValue());
        }
        return sj.toString();
    }

    private void getVectorClockFromMsg(String message) {
        String vectorClock = message.split("@")[2];

        String clocks[] = vectorClock.split(",");

        Map<String, Integer> tempMap = new HashMap<>();

        for (String clock : clocks) {
            String[] parts = clock.split(":");
            String id = parts[0] + ':' + parts[1];
            int val = Integer.parseInt(parts[2]);
            
            tempMap.put(id, val);
            
            if (localVectorClock.containsKey(id)) {
                // atualiza o relógio local em relacao ao processo apenas se o valor recebido for maior
                if (localVectorClock.get(id) < val) {
                    updateVectorClocks(id, val);
                }
            } else {
                // atualiza o vetor de relogios passando o id do processo
                updateVectorClocks(id, val);
            }
        }
        
        this.vectorClocks.put(message.split("@")[1], tempMap);
        System.out.println("Matriz de Relogios Logicos: \n" + stringMatrixClock());
    }
    // manda unicast para um membro do grupo
    public void sendUnicast(String msg, InetSocketAddress member) {
        try {
            byte[] buf = msg.getBytes();
            DatagramPacket packet = new DatagramPacket(buf, buf.length, member.getAddress(), member.getPort());
            socket.send(packet);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    // manda multicast para todos os membros do grupo
    public void sendMulticast(String msg) {
        try {
            byte[] buf = msg.getBytes();
            for (InetSocketAddress member : groupMembers) {
                DatagramPacket packet = new DatagramPacket(buf, buf.length, member.getAddress(), member.getPort());
                socket.send(packet);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    // envia mensagens para 1 ou todos os membros
    public void msend(String msg, IStableMulticast client) {
        try {
            @SuppressWarnings("resource")
            Scanner scanner = new Scanner(System.in);
            System.out.println("Enviar a mensagem em Mulicast ? (S/N)");
            String confirm = scanner.nextLine();

            updateVectorClocks(localId, getClockVal(localId) +1);
            String msg_ = msg + "@" + localId + "@" + stringVectorClock();

            if (confirm.equalsIgnoreCase("s")) {
                // multicast para todos os membros do grupo
                sendMulticast(msg_);
            } else {
                // unicast para cada membro do grupo
                for (InetSocketAddress member : this.getGroupMembers()) {
                    System.out.print("Enviar para " + member + "? (s/n): ");
                    String sendToMember = scanner.nextLine();

                    if (sendToMember.equalsIgnoreCase("s")) {
                        this.sendUnicast(msg_, member);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }
    // funcao para print do buffer de mensagens
    private String stringMessageBuffer() {
        StringBuilder sb = new StringBuilder();
        for (String key : messageBuffer.keySet()) {
            List<String> messages = messageBuffer.get(key);
            
            sb.append(key + ": {");
            for (String msg : messages) {
                sb.append(msg + ", ");
            }
            sb.append("}\n");
        }
        return sb.toString();
    }
    // funcao para print da matriz de relogios 
    private String stringMatrixClock() {
        StringBuilder sb = new StringBuilder();
        for (String key : vectorClocks.keySet()) {
            Map<String, Integer> clock = vectorClocks.get(key);
            
            sb.append(key + ": [");
            for (String key2 : clock.keySet()) {
                sb.append(key2 + ":" + clock.get(key2) + ", ");
            }
            sb.append("]\n");
        }
        return sb.toString();
    }

    public List<InetSocketAddress> getGroupMembers() {
        return Collections.unmodifiableList(groupMembers);
    }
    
    public void stabilizeMessages() {
        Map<String, List<String>> toExcludeList = new HashMap<>();

        for (String sender : messageBuffer.keySet()) {
            List<String> messages = messageBuffer.get(sender);

            List<String> toExcludeMsgs = new ArrayList<>();
            
            for (String msg : messages) {
                boolean canExclude = true;

                Map<String, Integer> tempMap = new HashMap<>();
                for (String msgClock : (msg.split("@")[2]).split(",") ) {
                    String id = msgClock.split(":")[0] + ":" + msgClock.split(":")[1];
                    Integer val = Integer.parseInt(msgClock.split(":")[2]);
                    tempMap.put(id, val);
                }

                for (Map<String, Integer> vectorClock : vectorClocks.values()) {
                    // Checa se todos os relógios vetoriais são maiores que o relógio vetorial da mensagem do sender
                    if (vectorClock.get(sender) <= tempMap.get(sender)) {
                        canExclude = false;
                        break;
                    }
                }
    
                // Checa se o relógio local é maior que o relógio vetorial da mensagem do sender
                if (localVectorClock.get(sender) <= tempMap.get(sender)) {
                    canExclude = false;
                }
    
                // Remove a mensagem do buffer
                if (canExclude) {
                    System.out.println("Excluindo mensagem estavel: " + msg);
                    toExcludeMsgs.add(msg);
                }
            }

            toExcludeList.put(sender, toExcludeMsgs);
        }
        
        for (String sender : toExcludeList.keySet()) {
            List<String> msgs = toExcludeList.get(sender);
            List<String> tempList = messageBuffer.get(sender);

            for (String msgToExclude : msgs) {
                tempList.remove(msgToExclude);
            }
            messageBuffer.put(sender, tempList);
        }
    }
}
