import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;

/**
 * Gateway de passageiros: servidor TCP (protocolo de texto do ENUNCIADO)
 * e, ao mesmo tempo, cliente e ouvinte RMI do despachante.
 *
 * Você pode criar campos, classes e métodos auxiliares à vontade.
 */
public class GatewayPassageiros extends UnicastRemoteObject implements OuvinteCorridas {

    public static final int PORTA_PADRAO = 5000;

    private final ServicoDespachante despachante;
    private ServerSocket servidor;
    private volatile boolean ativo;

    public GatewayPassageiros(ServicoDespachante despachante) throws RemoteException {
        super();
        this.despachante = despachante;
    }

    // =====================================================================
    // A IMPLEMENTAR
    // =====================================================================

    /** Executado em uma thread própria para cada passageiro conectado. */
    private void atenderConexao(Socket socket) {
        // TODO
    }

    @Override
    public void aoMudarEstado(EventoCorrida evento) throws RemoteException {
        // TODO
    }

    // =====================================================================
    // PRONTO
    // =====================================================================

    public void iniciar(int porta) throws IOException {
        despachante.registrarOuvinte(this);
        servidor = new ServerSocket(porta);
        ativo = true;
        Thread aceitador = new Thread(() -> {
            while (ativo) {
                try {
                    Socket s = servidor.accept();
                    new Thread(() -> atenderConexao(s), "passageiro-" + s.getPort()).start();
                } catch (IOException e) {
                    if (ativo) log("Erro no accept: " + e.getMessage());
                }
            }
        }, "gateway-accept");
        aceitador.start();
        log("Gateway ouvindo na porta " + porta);
    }

    public void parar() {
        ativo = false;
        try { if (servidor != null) servidor.close(); } catch (IOException ignored) { }
    }

    private void log(String msg) {
        System.out.printf("[%tT] [GATEWAY] %s%n", System.currentTimeMillis(), msg);
    }

    public static void main(String[] args) throws Exception {
        Registry registry = LocateRegistry.getRegistry("localhost", 1099);
        ServicoDespachante d = (ServicoDespachante) registry.lookup("DespachanteCorridas");
        GatewayPassageiros g = new GatewayPassageiros(d);
        g.iniciar(args.length > 0 ? Integer.parseInt(args[0]) : PORTA_PADRAO);
        Thread.currentThread().join();
    }
}
