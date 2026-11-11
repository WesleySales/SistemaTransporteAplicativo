import java.rmi.Remote;
import java.rmi.RemoteException;

public interface CallbackMotorista extends Remote {
    void aoAtribuir(AtribuicaoCorrida atribuicao) throws RemoteException;
    void aoCancelar(String atribuicaoId) throws RemoteException;
}
