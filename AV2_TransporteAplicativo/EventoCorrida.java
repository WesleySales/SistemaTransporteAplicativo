import java.io.Serializable;

/** Mudança de estado de uma corrida, enviada pelo despachante aos ouvintes. */
public class EventoCorrida implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String corridaId;
    private final StatusRequisicao status;
    private final String motoristaId;

    public EventoCorrida(String corridaId, StatusRequisicao status, String motoristaId) {
        this.corridaId = corridaId;
        this.status = status;
        this.motoristaId = motoristaId;
    }

    public String getCorridaId() { return corridaId; }
    public StatusRequisicao getStatus() { return status; }
    public String getMotoristaId() { return motoristaId; }

    @Override
    public String toString() {
        return "Evento[" + corridaId + " " + status + (motoristaId == null ? "" : " " + motoristaId) + "]";
    }
}
