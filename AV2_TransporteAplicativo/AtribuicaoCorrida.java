import java.io.Serializable;

/** Oferta de corrida enviada ao motorista via callback. */
public class AtribuicaoCorrida implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String atribuicaoId;
    private final String corridaId;
    private final InfoPassageiro passageiro;
    private final Localizacao origem;
    private final Localizacao destino;
    private final Prioridade prioridade;

    public AtribuicaoCorrida(String atribuicaoId, String corridaId, InfoPassageiro passageiro,
                             Localizacao origem, Localizacao destino, Prioridade prioridade) {
        this.atribuicaoId = atribuicaoId;
        this.corridaId = corridaId;
        this.passageiro = passageiro;
        this.origem = origem;
        this.destino = destino;
        this.prioridade = prioridade;
    }

    public String getAtribuicaoId() { return atribuicaoId; }
    public String getCorridaId() { return corridaId; }
    public InfoPassageiro getPassageiro() { return passageiro; }
    public Localizacao getOrigem() { return origem; }
    public Localizacao getDestino() { return destino; }
    public Prioridade getPrioridade() { return prioridade; }

    @Override
    public String toString() {
        return "Oferta[" + atribuicaoId + "] corrida=" + corridaId + " " + prioridade + " origem=" + origem;
    }
}
