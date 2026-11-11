import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.rmi.RemoteException;

/**
 * Motorista de linha de comando (RMI). Aceita toda oferta após 500 ms,
 * inicia após 1 s e conclui após 3 s.
 *
 * Uso: java ClienteMotorista <id> <nome> <placa> <lat> <lon>
 */
public class ClienteMotorista extends UnicastRemoteObject implements CallbackMotorista {

    private final ServicoDespachante despachante;

    public ClienteMotorista(ServicoDespachante despachante) throws RemoteException {
        super();
        this.despachante = despachante;
    }

    @Override
    public void aoAtribuir(AtribuicaoCorrida a) throws RemoteException {
        System.out.println("NOVA OFERTA: " + a);
        new Thread(() -> {
            try {
                Thread.sleep(500);
                if (!despachante.aceitarAtribuicao(a.getAtribuicaoId())) {
                    System.out.println("  aceite recusado pelo servidor: " + a.getAtribuicaoId());
                    return;
                }
                System.out.println("  aceita");
                Thread.sleep(1000);
                despachante.iniciarCorrida(a.getAtribuicaoId());
                System.out.println("  iniciada");
                Thread.sleep(3000);
                despachante.concluirCorrida(a.getAtribuicaoId());
                System.out.println("  concluida");
            } catch (Exception e) {
                System.out.println("  erro: " + e.getMessage());
            }
        }).start();
    }

    @Override
    public void aoCancelar(String atribuicaoId) throws RemoteException {
        System.out.println("OFERTA/CORRIDA CANCELADA: " + atribuicaoId);
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 5) {
            System.out.println("Uso: java ClienteMotorista <id> <nome> <placa> <lat> <lon>");
            System.out.println("Ex.: java ClienteMotorista M001 Joao ABC1234 -12.9714 -38.5014");
            return;
        }
        Registry registry = LocateRegistry.getRegistry("localhost", 1099);
        ServicoDespachante d = (ServicoDespachante) registry.lookup("DespachanteCorridas");
        ClienteMotorista cli = new ClienteMotorista(d);
        InfoMotorista info = new InfoMotorista(args[0], args[1], args[2],
                new Localizacao(Double.parseDouble(args[3]), Double.parseDouble(args[4])));
        System.out.println(d.registrarMotorista(info, cli) ? "Registrado: " + info : "ID já registrado");
        Thread.currentThread().join();
    }
}
