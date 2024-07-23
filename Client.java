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
        try (Scanner scanner = new Scanner(System.in)) {
            // Configuração do IP e porta do usuário
            // System.out.print("Enter your IP address: ");
            // String ip = scanner.nextLine();
            String ip = LocalIPFinder.getIp();

            System.out.print("Digite a porta: ");
            int port = Integer.parseInt(scanner.nextLine());

            // Inicializando o cliente
            Client client = new Client(ip, port);

            System.out.print("Digite mensagens para enviar: ");
            // Loop para enviar mensagens
            while (true) {
                if(scanner.hasNextLine()) 
                {
                    String msg = scanner.nextLine();
                    client.multicast.msend(msg, client);
                }
            }
        } catch (Exception e) {
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
