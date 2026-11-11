import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Despachante central (RMI).
 *
 * Convenções já usadas pelo código pronto desta classe:
 *  - todo acesso aos mapas abaixo, e aos objetos guardados neles, é feito com 'trava' adquirida;
 *  - quando um motorista está com uma corrida, motorista.getAtribuicaoAtual() e
 *    corrida.getAtribuicaoId() / corrida.getMotoristaId() apontam para a mesma Atribuicao.
 *
 * Você pode criar campos, classes e métodos auxiliares à vontade.
 * Não altere as interfaces remotas nem o protocolo do gateway.
 */
public class ServidorDespachante extends UnicastRemoteObject implements ServicoDespachante {

    public static final long TIMEOUT_CONFIRMACAO_MS = 2000;
    public static final long TIMEOUT_MATCHING_MS = 3000;

    protected final ReentrantLock trava = new ReentrantLock();
    protected final Map<String, Motorista> motoristas = new HashMap<>();
    protected final Map<String, RequisicaoCorrida> corridas = new HashMap<>();
    protected final Map<String, Atribuicao> atribuicoes = new HashMap<>();

    private final List<OuvinteCorridas> ouvintes = new CopyOnWriteArrayList<>();
    protected final ScheduledExecutorService agendador = Executors.newScheduledThreadPool(4);
    protected final ExecutorService executor = Executors.newCachedThreadPool();

    public ServidorDespachante() throws RemoteException {
        super();
    }

    // =====================================================================
    // A IMPLEMENTAR
    // =====================================================================

    @Override
    public String solicitarCorrida(InfoPassageiro passageiro, Localizacao origem, Localizacao destino,
                                   Prioridade prioridade) throws RemoteException {
        // TODO
        throw new UnsupportedOperationException("solicitarCorrida");
    }

    @Override
    public boolean aceitarAtribuicao(String atribuicaoId) throws RemoteException {
        // TODO
        throw new UnsupportedOperationException("aceitarAtribuicao");
    }

    @Override
    public boolean cancelarCorrida(String corridaId) throws RemoteException {
        // TODO
        throw new UnsupportedOperationException("cancelarCorrida");
    }

    /** Chamado pelo código pronto sempre que um motorista pode ter ficado livre. */
    private void motoristaFicouDisponivel(String motoristaId) {
        // TODO
    }

    // =====================================================================
    // PRONTO
    // =====================================================================

    @Override
    public boolean registrarMotorista(InfoMotorista info, CallbackMotorista callback) throws RemoteException {
        trava.lock();
        try {
            if (motoristas.containsKey(info.getMotoristaId())) return false;
            motoristas.put(info.getMotoristaId(), new Motorista(info, callback));
        } finally {
            trava.unlock();
        }
        log("REGISTRO " + info);
        motoristaFicouDisponivel(info.getMotoristaId());
        return true;
    }

    @Override
    public void atualizarStatus(String motoristaId, StatusMotorista status) throws RemoteException {
        trava.lock();
        try {
            Motorista m = motoristas.get(motoristaId);
            if (m == null) throw new RemoteException("Motorista desconhecido: " + motoristaId);
            m.setStatus(status);
        } finally {
            trava.unlock();
        }
        log("STATUS " + motoristaId + " -> " + status);
        if (status == StatusMotorista.DISPONIVEL) motoristaFicouDisponivel(motoristaId);
    }

    @Override
    public void iniciarCorrida(String atribuicaoId) throws RemoteException {
        EventoCorrida evento;
        trava.lock();
        try {
            Atribuicao a = atribuicoes.get(atribuicaoId);
            RequisicaoCorrida c = (a == null) ? null : corridas.get(a.getCorridaId());
            if (c == null || c.getStatus() != StatusRequisicao.ATRIBUIDA || !atribuicaoId.equals(c.getAtribuicaoId()))
                throw new RemoteException("Atribuição não está ATRIBUIDA: " + atribuicaoId);
            c.setStatus(StatusRequisicao.INICIADA);
            Motorista m = motoristas.get(a.getMotoristaId());
            if (m != null) m.setStatus(StatusMotorista.EM_CORRIDA);
            evento = new EventoCorrida(c.getCorridaId(), StatusRequisicao.INICIADA, a.getMotoristaId());
        } finally {
            trava.unlock();
        }
        log("INICIADA " + evento.getCorridaId());
        notificarOuvintes(evento);
    }

    @Override
    public void concluirCorrida(String atribuicaoId) throws RemoteException {
        EventoCorrida evento;
        String motoristaId;
        trava.lock();
        try {
            Atribuicao a = atribuicoes.get(atribuicaoId);
            RequisicaoCorrida c = (a == null) ? null : corridas.get(a.getCorridaId());
            if (c == null || c.getStatus() != StatusRequisicao.INICIADA || !atribuicaoId.equals(c.getAtribuicaoId()))
                throw new RemoteException("Atribuição não está INICIADA: " + atribuicaoId);
            c.setStatus(StatusRequisicao.CONCLUIDA);
            motoristaId = a.getMotoristaId();
            Motorista m = motoristas.get(motoristaId);
            if (m != null) {
                m.setStatus(StatusMotorista.DISPONIVEL);
                m.setAtribuicaoAtual(null);
            }
            evento = new EventoCorrida(c.getCorridaId(), StatusRequisicao.CONCLUIDA, motoristaId);
        } finally {
            trava.unlock();
        }
        log("CONCLUIDA " + evento.getCorridaId());
        notificarOuvintes(evento);
        motoristaFicouDisponivel(motoristaId);
    }

    @Override
    public BilheteCorrida consultarCorrida(String corridaId) throws RemoteException {
        trava.lock();
        try {
            RequisicaoCorrida c = corridas.get(corridaId);
            if (c == null) throw new RemoteException("Corrida desconhecida: " + corridaId);
            return c.toBilhete();
        } finally {
            trava.unlock();
        }
    }

    @Override
    public void registrarOuvinte(OuvinteCorridas ouvinte) throws RemoteException {
        ouvintes.add(ouvinte);
    }

    /** Entrega o evento a todos os ouvintes, na thread de quem chamou. */
    protected void notificarOuvintes(EventoCorrida evento) {
        for (OuvinteCorridas o : ouvintes) {
            try {
                o.aoMudarEstado(evento);
            } catch (RemoteException e) {
                log("Ouvinte inacessível, removido: " + e.getMessage());
                ouvintes.remove(o);
            }
        }
    }

    protected double distancia(Localizacao a, Localizacao b) {
        double dx = a.getLatitude() - b.getLatitude();
        double dy = a.getLongitude() - b.getLongitude();
        return Math.sqrt(dx * dx + dy * dy);
    }

    protected static String novoId() {
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    protected void log(String msg) {
        System.out.printf("[%tT] %s%n", System.currentTimeMillis(), msg);
    }

    public void encerrar() {
        agendador.shutdownNow();
        executor.shutdownNow();
    }

    public static void main(String[] args) throws Exception {
        ServidorDespachante s = new ServidorDespachante();
        Registry registry = LocateRegistry.createRegistry(1099);
        registry.rebind("DespachanteCorridas", s);
        System.out.println("Despachante pronto em localhost:1099 (DespachanteCorridas)");
        Thread.currentThread().join();
    }
}
