import java.rmi.Remote;
import java.rmi.RemoteException;

public interface ServicoDespachante extends Remote {

    // === MOTORISTAS ===
    boolean registrarMotorista(InfoMotorista info, CallbackMotorista callback) throws RemoteException;
    void atualizarStatus(String motoristaId, StatusMotorista status) throws RemoteException;
    boolean aceitarAtribuicao(String atribuicaoId) throws RemoteException;
    void iniciarCorrida(String atribuicaoId) throws RemoteException;
    void concluirCorrida(String atribuicaoId) throws RemoteException;

    // === PASSAGEIROS (usado pelo gateway) ===
    String solicitarCorrida(InfoPassageiro passageiro, Localizacao origem, Localizacao destino,
                            Prioridade prioridade) throws RemoteException;
    BilheteCorrida consultarCorrida(String corridaId) throws RemoteException;
    boolean cancelarCorrida(String corridaId) throws RemoteException;

    // === EVENTOS ===
    void registrarOuvinte(OuvinteCorridas ouvinte) throws RemoteException;
}
