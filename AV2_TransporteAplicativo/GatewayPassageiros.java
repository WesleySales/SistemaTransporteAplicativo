import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

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


    private static final int LIMITE_FILA_SAIDA = 1024;
    private static final long VALIDADE_ADIANTADOS_NS = TimeUnit.SECONDS.toNanos(10);

    // Protegidos por 'estado'. Com ele adquirido não se faz chamada RMI nem escrita em socket.
    private final Object estado = new Object();
    /** Corrida ainda não encerrada -> conexão do passageiro dono. */
    private final Map<String, Conexao> donos = new HashMap<>();
    /** Eventos que chegaram antes de o gateway conhecer a corrida (antes do OK), por ordem de chegada. */
    private final LinkedHashMap<String, Adiantados> adiantados = new LinkedHashMap<>();

    private static final class Adiantados {
        final long desde = System.nanoTime();
        final List<EventoCorrida> eventos = new ArrayList<>();
    }

    /**
     * Um passageiro conectado. Quem quer enviar só enfileira a linha; quem escreve no socket é a
     * thread escritora desta conexão. Assim um passageiro que não lê não atrasa ninguém (R9).
     */
    private static final class Conexao {
        private static final String FIM = new String("FIM");   // sentinela, comparada por identidade

        private final Socket socket;
        private final BlockingQueue<String> saida = new LinkedBlockingQueue<>(LIMITE_FILA_SAIDA);
        private final Thread escritora;
        private volatile boolean fechada;

        Conexao(Socket socket) {
            this.socket = socket;
            this.escritora = new Thread(this::escrever, "saida-" + socket.getPort());
            this.escritora.setDaemon(true);
        }

        /** Nunca bloqueia. Se a fila encher (passageiro não lê), a conexão é derrubada. */
        void enviar(String linha) {
            if (fechada) return;
            if (!saida.offer(linha)) fechar();
        }

        /** O passageiro parou de enviar: entrega o que já está na fila e fecha. */
        void encerrar() {
            if (fechada) return;
            fechada = true;
            if (!saida.offer(FIM)) fechar();
        }

        void fechar() {
            fechada = true;
            escritora.interrupt();
            try { socket.close(); } catch (IOException ignored) { }
        }

        private void escrever() {
            try {
                Writer w = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
                while (true) {
                    String linha = saida.take();
                    if (linha == FIM) break;
                    w.write(linha);
                    w.write('\n');
                    w.flush();
                }
            } catch (IOException | InterruptedException e) {
                // conexão encerrada
            } finally {
                fechar();
            }
        }
    }

    /** Executado em uma thread própria para cada passageiro conectado. */
    private void atenderConexao(Socket socket) {
        Conexao con = new Conexao(socket);
        con.escritora.start();
        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            String linha;
            while ((linha = in.readLine()) != null) tratarLinha(con, linha);
            con.encerrar();
        } catch (IOException e) {
            con.fechar();
        } finally {
            aoDesconectar(con);
        }
    }

    /** Qualquer problema na linha vira uma resposta ERRO; a conexão continua aberta. */
    private void tratarLinha(Conexao con, String linha) {
        String[] t = linha.trim().split("\\s+");
        try {
            if (t[0].equalsIgnoreCase("SOLICITAR")) solicitar(con, t);
            else if (t[0].equalsIgnoreCase("CANCELAR")) cancelar(con, t);
            else con.enviar("ERRO comando desconhecido");
        } catch (RemoteException e) {
            log("Despachante inacessível: " + e.getMessage());
            con.enviar("ERRO despachante indisponivel");
        } catch (RuntimeException e) {
            log("Falha ao tratar '" + linha + "': " + e);
            con.enviar("ERRO falha interna");
        }
    }

    private void solicitar(Conexao con, String[] t) throws RemoteException {
        if (t.length != 8) {
            con.enviar("ERRO uso: SOLICITAR <id> <nome> <oLat> <oLon> <dLat> <dLon> <VIP|STANDARD>");
            return;
        }
        Localizacao origem, destino;
        Prioridade prioridade;
        try {
            origem = new Localizacao(coordenada(t[3]), coordenada(t[4]));
            destino = new Localizacao(coordenada(t[5]), coordenada(t[6]));
            prioridade = Prioridade.valueOf(t[7].toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            con.enviar("ERRO parametros invalidos");
            return;
        }
        String id = despachante.solicitarCorrida(new InfoPassageiro(t[1], t[2]), origem, destino, prioridade);
        // Num só bloco: a corrida passa a ter dono, o OK entra na fila e só depois os eventos que
        // chegaram adiantados. Evento que chegar daqui em diante já encontra o dono e entra após o OK (R8).
        synchronized (estado) {
            donos.put(id, con);
            con.enviar("OK " + id);
            Adiantados a = adiantados.remove(id);
            if (a != null) for (EventoCorrida e : a.eventos) entregar(e);
        }
    }

    private void cancelar(Conexao con, String[] t) throws RemoteException {
        if (t.length != 2) {
            con.enviar("ERRO uso: CANCELAR <corridaId>");
            return;
        }
        String id = t[1];
        boolean propria;
        synchronized (estado) {
            propria = donos.get(id) == con;
        }
        if (!propria || !despachante.cancelarCorrida(id)) {
            con.enviar("NEGADO " + id);
            return;
        }
        synchronized (estado) {
            // Se o evento CANCELADA já passou por entregar(), a corrida saiu de 'donos' e o
            // passageiro já foi avisado: não repete (R8).
            if (donos.remove(id) != null) con.enviar("CANCELADA " + id);
        }
    }

    private static double coordenada(String texto) {
        double v = Double.parseDouble(texto);
        if (!Double.isFinite(v)) throw new NumberFormatException(texto);
        return v;
    }

    /** D3 = CANCELAR: o passageiro saiu, então as corridas dele ainda não iniciadas são canceladas. */
    private void aoDesconectar(Conexao con) {
        List<String> abertas = new ArrayList<>();
        synchronized (estado) {
            for (Map.Entry<String, Conexao> e : donos.entrySet())
                if (e.getValue() == con) abertas.add(e.getKey());
        }
        for (String id : abertas) {
            try {
                if (despachante.cancelarCorrida(id)) {
                    log("Passageiro desconectou: corrida " + id + " cancelada");
                    synchronized (estado) { donos.remove(id); }
                } else {
                    // já iniciada: segue até o fim, e entregar() descarta os eventos dela
                    log("Passageiro desconectou: corrida " + id + " já iniciada, mantida");
                }
            } catch (RemoteException | RuntimeException e) {
                log("Não foi possível cancelar " + id + ": " + e.getMessage());
            }
        }
    }

    @Override
    public void aoMudarEstado(EventoCorrida evento) throws RemoteException {
        if (evento == null || evento.getCorridaId() == null || evento.getStatus() == null) return;
        try {
            synchronized (estado) {
                if (donos.containsKey(evento.getCorridaId())) entregar(evento);
                else guardarAdiantado(evento);
            }
        } catch (RuntimeException e) {
            log("Evento descartado " + evento + ": " + e);   // nada aqui pode derrubar o gateway
        }
    }

    /**
     * Exige 'estado'. Enfileira o evento para o dono da corrida, no formato do protocolo.
     * Evento final tira a corrida de 'donos', então nada mais é entregue sobre ela.
     * Se o dono já desconectou, enviar() não faz nada.
     */
    private void entregar(EventoCorrida e) {
        String id = e.getCorridaId();
        Conexao dono = donos.get(id);
        if (dono == null) return;
        switch (e.getStatus()) {
            case ATRIBUIDA:
                dono.enviar("ATRIBUIDA " + id + " " + e.getMotoristaId());
                break;
            case INICIADA:
                dono.enviar("INICIADA " + id);
                break;
            case CONCLUIDA:
            case EXPIRADA:
            case CANCELADA:
                dono.enviar(e.getStatus() + " " + id);
                donos.remove(id);
                break;
            default:
                break;
        }
    }

    /** Exige 'estado'. Guarda o evento até solicitar() conhecer a corrida; entradas velhas são descartadas. */
    private void guardarAdiantado(EventoCorrida e) {
        long agora = System.nanoTime();
        Iterator<Adiantados> it = adiantados.values().iterator();
        while (it.hasNext() && agora - it.next().desde > VALIDADE_ADIANTADOS_NS) it.remove();
        adiantados.computeIfAbsent(e.getCorridaId(), k -> new Adiantados()).eventos.add(e);
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
