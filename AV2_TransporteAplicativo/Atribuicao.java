/** Oferta de uma corrida a um motorista (estado no servidor). */
public class Atribuicao {
    private final String atribuicaoId;
    private final String corridaId;
    private final String motoristaId;
    private final long timestamp;

    public Atribuicao(String atribuicaoId, String corridaId, String motoristaId) {
        this.atribuicaoId = atribuicaoId;
        this.corridaId = corridaId;
        this.motoristaId = motoristaId;
        this.timestamp = System.currentTimeMillis();
    }

    public String getAtribuicaoId() { return atribuicaoId; }
    public String getCorridaId() { return corridaId; }
    public String getMotoristaId() { return motoristaId; }
    public long getTimestamp() { return timestamp; }
}
