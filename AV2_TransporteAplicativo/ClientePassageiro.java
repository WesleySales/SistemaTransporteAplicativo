import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Terminal de passageiro (socket TCP). Envia cada linha digitada ao gateway
 * e imprime tudo o que o gateway enviar.
 *
 * Uso: java ClientePassageiro [host] [porta]
 * Ex.: SOLICITAR P001 Ana -12.9714 -38.5014 -12.9800 -38.5100 VIP
 */
public class ClientePassageiro {
    public static void main(String[] args) throws Exception {
        String host = args.length > 0 ? args[0] : "localhost";
        int porta = args.length > 1 ? Integer.parseInt(args[1]) : 5000;

        try (Socket s = new Socket(host, porta)) {
            BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
            PrintWriter out = new PrintWriter(new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8), true);

            Thread leitor = new Thread(() -> {
                try {
                    String linha;
                    while ((linha = in.readLine()) != null) System.out.println("<< " + linha);
                } catch (IOException ignored) { }
                System.out.println("(conexão encerrada)");
                System.exit(0);
            });
            leitor.setDaemon(true);
            leitor.start();

            BufferedReader teclado = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
            String linha;
            while ((linha = teclado.readLine()) != null) out.println(linha);
        }
    }
}
