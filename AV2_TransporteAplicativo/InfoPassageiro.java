import java.io.Serializable;

public class InfoPassageiro implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String passageiroId;
    private final String nome;

    public InfoPassageiro(String passageiroId, String nome) {
        this.passageiroId = passageiroId;
        this.nome = nome;
    }

    public String getPassageiroId() { return passageiroId; }
    public String getNome() { return nome; }

    @Override
    public String toString() {
        return nome + " (" + passageiroId + ")";
    }
}
