/** Estado de um motorista no servidor. */
public class Motorista {
    private final InfoMotorista info;
    private final CallbackMotorista callback;
    private StatusMotorista status;
    private String atribuicaoAtual;

    public Motorista(InfoMotorista info, CallbackMotorista callback) {
        this.info = info;
        this.callback = callback;
        this.status = StatusMotorista.DISPONIVEL;
    }

    public String getId() { return info.getMotoristaId(); }
    public InfoMotorista getInfo() { return info; }
    public CallbackMotorista getCallback() { return callback; }
    public StatusMotorista getStatus() { return status; }
    public String getAtribuicaoAtual() { return atribuicaoAtual; }

    public void setStatus(StatusMotorista status) { this.status = status; }
    public void setAtribuicaoAtual(String atribuicaoId) { this.atribuicaoAtual = atribuicaoId; }

    public boolean isDisponivel() {
        return status == StatusMotorista.DISPONIVEL && atribuicaoAtual == null;
    }
}
