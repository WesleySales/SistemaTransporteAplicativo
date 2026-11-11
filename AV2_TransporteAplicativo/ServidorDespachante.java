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


    /** Dados de uma corrida enquanto ela procura motorista (PENDENTE ou OFERTADA). */
    private static final class Busca {
        final RequisicaoCorrida corrida;
        final long sequencia;                              // ordem de chegada, desempata timestamps iguais
        final Set<String> descartados = new HashSet<>();   // motoristas que deixaram a oferta expirar (R5)
        ScheduledFuture<?> prazoCorrida;                   // R6
        ScheduledFuture<?> prazoOferta;                    // R5

        Busca(RequisicaoCorrida corrida, long sequencia) {
            this.corrida = corrida;
            this.sequencia = sequencia;
        }
    }

    /** Chamada remota a um motorista. */
    private interface Aviso {
        void enviar(CallbackMotorista callback) throws RemoteException;
    }

    /** R4: VIP antes de STANDARD; na mesma prioridade, a mais antiga primeiro. */
    private static final Comparator<Busca> ORDEM_R4 = Comparator
            .comparingInt((Busca b) -> b.corrida.getPrioridade() == Prioridade.VIP ? 0 : 1)
            .thenComparingLong(b -> b.corrida.getTimestamp())
            .thenComparingLong(b -> b.sequencia);

    // Estado auxiliar: como os mapas acima, só é acessado com 'trava' adquirida.
    private final Map<String, Busca> buscas = new HashMap<>();
    private final TreeSet<Busca> espera = new TreeSet<>(ORDEM_R4);   // corridas PENDENTES, na ordem da R4
    private final Map<String, CompletableFuture<Void>> avisosPorMotorista = new HashMap<>();
    private long proximaSequencia;

    @Override
    public String solicitarCorrida(InfoPassageiro passageiro, Localizacao origem, Localizacao destino,
                                   Prioridade prioridade) throws RemoteException {
        if (passageiro == null || origem == null || destino == null || prioridade == null)
            throw new RemoteException("Solicitação incompleta");
        RequisicaoCorrida c;
        trava.lock();
        try {
            String id;
            do { id = novoId(); } while (corridas.containsKey(id));
            c = new RequisicaoCorrida(id, passageiro, origem, destino, prioridade);
            corridas.put(id, c);
            Busca b = new Busca(c, proximaSequencia++);
            buscas.put(id, b);
            espera.add(b);
            String corridaId = id;
            b.prazoCorrida = agendar(() -> expirarCorrida(corridaId), TIMEOUT_MATCHING_MS);
            casar();
        } finally {
            trava.unlock();
        }
        log("SOLICITADA " + c.getCorridaId() + " " + prioridade + " " + passageiro);
        return c.getCorridaId();
    }

    @Override
    public boolean aceitarAtribuicao(String atribuicaoId) throws RemoteException {
        EventoCorrida evento;
        trava.lock();
        try {
            Atribuicao a = atribuicoes.get(atribuicaoId);
            if (a == null) return false;                       // oferta já retirada (ou nunca existiu)
            RequisicaoCorrida c = corridas.get(a.getCorridaId());
            Motorista m = motoristas.get(a.getMotoristaId());
            if (c == null || m == null) return false;
            if (c.getStatus() != StatusRequisicao.OFERTADA
                    || !atribuicaoId.equals(c.getAtribuicaoId())
                    || !atribuicaoId.equals(m.getAtribuicaoAtual())) return false;
            // Prazos conferidos pelo relógio: não dependem de o temporizador já ter rodado.
            long agora = System.currentTimeMillis();
            if (agora - a.getTimestamp() >= TIMEOUT_CONFIRMACAO_MS
                    || agora - c.getTimestamp() >= TIMEOUT_MATCHING_MS) return false;
            encerrarBusca(c);
            c.setStatus(StatusRequisicao.ATRIBUIDA);
            evento = new EventoCorrida(c.getCorridaId(), StatusRequisicao.ATRIBUIDA, m.getId());
        } finally {
            trava.unlock();
        }
        publicar(evento);
        return true;
    }

    @Override
    public boolean cancelarCorrida(String corridaId) throws RemoteException {
        EventoCorrida evento;
        trava.lock();
        try {
            RequisicaoCorrida c = corridas.get(corridaId);
            if (c == null) return false;
            StatusRequisicao s = c.getStatus();
            if (s != StatusRequisicao.PENDENTE && s != StatusRequisicao.OFERTADA
                    && s != StatusRequisicao.ATRIBUIDA) return false;
            encerrarBusca(c);
            boolean liberouMotorista = retirarAtribuicao(c);
            c.setStatus(StatusRequisicao.CANCELADA);
            evento = new EventoCorrida(corridaId, StatusRequisicao.CANCELADA, null);
            if (liberouMotorista) casar();
        } finally {
            trava.unlock();
        }
        publicar(evento);
        return true;
    }

    /** Chamado pelo código pronto sempre que um motorista pode ter ficado livre. */
    private void motoristaFicouDisponivel(String motoristaId) {
        trava.lock();
        try {
            casar();   // só usa motoristas com isDisponivel(), então quem ainda tem corrida não recebe outra (R1)
        } finally {
            trava.unlock();
        }
    }

    /** Temporizador da R5: a oferta não foi aceita em 2 s. */
    private void expirarOferta(String atribuicaoId) {
        String motoristaId;
        String corridaId;
        trava.lock();
        try {
            Atribuicao a = atribuicoes.get(atribuicaoId);
            if (a == null) return;
            RequisicaoCorrida c = corridas.get(a.getCorridaId());
            Busca b = buscas.get(a.getCorridaId());
            if (c == null || b == null || c.getStatus() != StatusRequisicao.OFERTADA
                    || !atribuicaoId.equals(c.getAtribuicaoId())) return;
            motoristaId = a.getMotoristaId();
            corridaId = c.getCorridaId();
            b.descartados.add(motoristaId);
            b.prazoOferta = null;
            retirarAtribuicao(c);
            c.setStatus(StatusRequisicao.PENDENTE);
            espera.add(b);   // volta com a antiguidade original
            casar();
        } finally {
            trava.unlock();
        }
        log("OFERTA EXPIROU " + corridaId + " (" + motoristaId + ")");
    }

    /** Temporizador da R6: a corrida não foi aceita em 3 s. */
    private void expirarCorrida(String corridaId) {
        EventoCorrida evento;
        trava.lock();
        try {
            RequisicaoCorrida c = corridas.get(corridaId);
            if (c == null) return;
            StatusRequisicao s = c.getStatus();
            if (s != StatusRequisicao.PENDENTE && s != StatusRequisicao.OFERTADA) return;
            encerrarBusca(c);
            boolean liberouMotorista = retirarAtribuicao(c);
            c.setStatus(StatusRequisicao.EXPIRADA);
            evento = new EventoCorrida(corridaId, StatusRequisicao.EXPIRADA, null);
            if (liberouMotorista) casar();
        } finally {
            trava.unlock();
        }
        publicar(evento);
    }

    /**
     * Exige 'trava'. Oferece as corridas em espera, na ordem da R4, aos motoristas livres;
     * cada corrida vai para o motorista livre mais próximo da origem que ainda não a deixou expirar.
     */
    private void casar() {
        if (espera.isEmpty()) return;
        List<Motorista> livres = new ArrayList<>();
        for (Motorista m : motoristas.values()) if (m.isDisponivel()) livres.add(m);
        Iterator<Busca> it = espera.iterator();
        while (it.hasNext() && !livres.isEmpty()) {
            Busca b = it.next();
            Motorista escolhido = maisProximo(livres, b);
            if (escolhido == null) continue;   // os livres já deixaram esta expirar: tenta a próxima da fila
            it.remove();
            livres.remove(escolhido);
            ofertar(b, escolhido);
        }
    }

    /** Exige 'trava'. */
    private Motorista maisProximo(List<Motorista> livres, Busca b) {
        Motorista melhor = null;
        double menor = Double.MAX_VALUE;
        for (Motorista m : livres) {
            if (b.descartados.contains(m.getId())) continue;
            double d = distancia(m.getInfo().getPosicao(), b.corrida.getOrigem());
            if (melhor == null || d < menor || (d == menor && m.getId().compareTo(melhor.getId()) < 0)) {
                melhor = m;
                menor = d;
            }
        }
        return melhor;
    }

    /** Exige 'trava'. Reserva o motorista para a corrida e agenda o envio da oferta e o prazo da R5. */
    private void ofertar(Busca b, Motorista m) {
        RequisicaoCorrida c = b.corrida;
        String atribuicaoId;
        do { atribuicaoId = novoId(); } while (atribuicoes.containsKey(atribuicaoId));
        Atribuicao a = new Atribuicao(atribuicaoId, c.getCorridaId(), m.getId());
        atribuicoes.put(atribuicaoId, a);
        c.setStatus(StatusRequisicao.OFERTADA);
        c.setAtribuicaoId(atribuicaoId);
        c.setMotoristaId(m.getId());
        m.setAtribuicaoAtual(atribuicaoId);
        String id = atribuicaoId;
        b.prazoOferta = agendar(() -> expirarOferta(id), TIMEOUT_CONFIRMACAO_MS);
        AtribuicaoCorrida oferta = new AtribuicaoCorrida(atribuicaoId, c.getCorridaId(), c.getPassageiro(),
                c.getOrigem(), c.getDestino(), c.getPrioridade());
        avisarMotorista(m, "OFERTA " + c.getCorridaId(), cb -> cb.aoAtribuir(oferta));
    }

    /**
     * Exige 'trava'. Desfaz a oferta/atribuição atual da corrida: o motorista fica livre e é avisado.
     * Devolve true se havia um motorista preso a ela.
     */
    private boolean retirarAtribuicao(RequisicaoCorrida c) {
        String atribuicaoId = c.getAtribuicaoId();
        if (atribuicaoId == null) return false;
        Atribuicao a = atribuicoes.remove(atribuicaoId);
        c.setAtribuicaoId(null);
        c.setMotoristaId(null);
        Motorista m = (a == null) ? null : motoristas.get(a.getMotoristaId());
        if (m == null) return false;
        if (atribuicaoId.equals(m.getAtribuicaoAtual())) m.setAtribuicaoAtual(null);
        avisarMotorista(m, "RETIRADA " + c.getCorridaId(), cb -> cb.aoCancelar(atribuicaoId));
        return true;
    }

    /** Exige 'trava'. A corrida deixou de procurar motorista: sai da fila e perde os temporizadores. */
    private void encerrarBusca(RequisicaoCorrida c) {
        Busca b = buscas.remove(c.getCorridaId());
        if (b == null) return;
        espera.remove(b);
        if (b.prazoCorrida != null) b.prazoCorrida.cancel(false);
        if (b.prazoOferta != null) b.prazoOferta.cancel(false);
    }

    /**
     * Exige 'trava', mas a chamada remota roda depois, numa thread do 'executor': motorista lento
     * não segura a trava nem quem está atendendo outra corrida (R9). As chamadas a um mesmo
     * motorista são encadeadas, para chegarem na ordem em que foram decididas.
     */
    private void avisarMotorista(Motorista m, String descricao, Aviso aviso) {
        String motoristaId = m.getId();
        CallbackMotorista callback = m.getCallback();
        Runnable tarefa = () -> {
            log(descricao + " -> " + motoristaId);
            try {
                aviso.enviar(callback);
            } catch (Exception e) {
                log("Motorista " + motoristaId + " não recebeu o aviso: " + e.getMessage());
            }
        };
        CompletableFuture<Void> anterior =
                avisosPorMotorista.getOrDefault(motoristaId, CompletableFuture.completedFuture(null));
        avisosPorMotorista.put(motoristaId, anterior.thenRunAsync(tarefa, executor));
    }

    /** Fora da 'trava': registra e entrega o evento aos ouvintes, na thread de quem chamou. */
    private void publicar(EventoCorrida evento) {
        log(evento.getStatus() + " " + evento.getCorridaId()
                + (evento.getMotoristaId() == null ? "" : " " + evento.getMotoristaId()));
        try {
            notificarOuvintes(evento);
        } catch (RuntimeException e) {
            log("Falha ao notificar " + evento + ": " + e);
        }
    }

    private ScheduledFuture<?> agendar(Runnable tarefa, long atrasoMs) {
        return agendador.schedule(() -> {
            try {
                tarefa.run();
            } catch (RuntimeException e) {
                log("Falha em temporizador: " + e);
            }
        }, atrasoMs, TimeUnit.MILLISECONDS);
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
