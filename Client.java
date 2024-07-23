import StableMulticast.*;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Scanner;

// Classe com método para pegar o IP automticamente
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

// Classe do cliente
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
}
