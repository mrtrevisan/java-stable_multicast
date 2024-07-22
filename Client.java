import StableMulticast.*;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Scanner;

class LocalIPFinder {  
    public static String getIp() {  
        try {  
            InetAddress localhost = InetAddress.getLocalHost();  
            return localhost.getHostAddress();
        } catch (Exception ex) {  
            ex.printStackTrace(); 
            return null; 
        }  
    }  
}  

public class Client implements IStableMulticast {
    private StableMulticast multicast;

    public Client(String ip, int port) throws IOException {
        multicast = new StableMulticast(ip, port, this);
    }

    @Override
    public void deliver(String msg) {
        System.out.println("Mensagem recebida: " + msg);
    }

    public static void main(String[] args) {
        try {
            // Definindo o endereço IP e porta do grupo multicast
            String ip = "230.0.0.0";
            int port = 4321;

            // Inicializando o cliente
            Client client = new Client(ip, port);

            // Gerenciando a interface de usuário para enviar mensagens
            client.startUserInterface();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void startUserInterface() {
        new Thread(() -> {
            try (Scanner scanner = new Scanner(System.in)) {
                while (true) {
                    System.out.print("Digite a mensagem para enviar: ");
                    String msg = scanner.nextLine();
                    
                    System.out.print("Enviar a todos os membros do grupo? (s/n): ");
                    String sendToAll = scanner.nextLine();

                    if (sendToAll.equalsIgnoreCase("s")) {
                        multicast.msend(msg, this);
                    } else {
                        for (InetSocketAddress member : multicast.getGroupMembers()) {
                            System.out.print("Enviar para " + member + "? (s/n): ");
                            String sendToMember = scanner.nextLine();
                            if (sendToMember.equalsIgnoreCase("s")) {
                                multicast.sendUnicast(msg, member);
                            }
                        }
                    }
                }
            }
        }).start();
    }
}
