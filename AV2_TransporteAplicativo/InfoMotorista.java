import java.io.Serializable;

public class InfoMotorista implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String motoristaId;
    private final String nome;
    private final String placa;
    private final Localizacao posicao;

    public InfoMotorista(String motoristaId, String nome, String placa, Localizacao posicao) {
        this.motoristaId = motoristaId;
        this.nome = nome;
        this.placa = placa;
        this.posicao = posicao;
    }

    public String getMotoristaId() { return motoristaId; }
    public String getNome() { return nome; }
    public String getPlaca() { return placa; }
    public Localizacao getPosicao() { return posicao; }

    @Override
    public String toString() {
        return nome + " (" + motoristaId + ") " + placa;
    }
}
