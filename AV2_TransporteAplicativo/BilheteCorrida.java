import java.io.Serializable;

public class BilheteCorrida implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String corridaId;
    private final StatusRequisicao status;
    private final String motoristaId;
    private final long timestamp;

    public BilheteCorrida(String corridaId, StatusRequisicao status, String motoristaId, long timestamp) {
        this.corridaId = corridaId;
        this.status = status;
        this.motoristaId = motoristaId;
        this.timestamp = timestamp;
    }

    public String getCorridaId() { return corridaId; }
    public StatusRequisicao getStatus() { return status; }
    public String getMotoristaId() { return motoristaId; }
    public long getTimestamp() { return timestamp; }

    @Override
    public String toString() {
        return "Bilhete[" + corridaId + "] " + status + " motorista=" + (motoristaId == null ? "-" : motoristaId);
    }
}
