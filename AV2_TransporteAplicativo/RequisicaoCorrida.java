/** Estado de uma corrida no servidor. */
public class RequisicaoCorrida {
    private final String corridaId;
    private final InfoPassageiro passageiro;
    private final Localizacao origem;
    private final Localizacao destino;
    private final Prioridade prioridade;
    private final long timestamp;
    private StatusRequisicao status;
    private String motoristaId;
    private String atribuicaoId;

    public RequisicaoCorrida(String corridaId, InfoPassageiro passageiro, Localizacao origem,
                             Localizacao destino, Prioridade prioridade) {
        this.corridaId = corridaId;
        this.passageiro = passageiro;
        this.origem = origem;
        this.destino = destino;
        this.prioridade = prioridade;
        this.timestamp = System.currentTimeMillis();
        this.status = StatusRequisicao.PENDENTE;
    }

    public String getCorridaId() { return corridaId; }
    public InfoPassageiro getPassageiro() { return passageiro; }
    public Localizacao getOrigem() { return origem; }
    public Localizacao getDestino() { return destino; }
    public Prioridade getPrioridade() { return prioridade; }
    public long getTimestamp() { return timestamp; }
    public StatusRequisicao getStatus() { return status; }
    public String getMotoristaId() { return motoristaId; }
    public String getAtribuicaoId() { return atribuicaoId; }

    public void setStatus(StatusRequisicao status) { this.status = status; }
    public void setMotoristaId(String motoristaId) { this.motoristaId = motoristaId; }
    public void setAtribuicaoId(String atribuicaoId) { this.atribuicaoId = atribuicaoId; }

    public BilheteCorrida toBilhete() {
        return new BilheteCorrida(corridaId, status, motoristaId, timestamp);
    }
}
