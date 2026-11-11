import java.rmi.Remote;
import java.rmi.RemoteException;

public interface OuvinteCorridas extends Remote {
    void aoMudarEstado(EventoCorrida evento) throws RemoteException;
}
