import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;

/**
 * TESTE DE FUMAÇA (público).
 * Verifica só o básico do protocolo. Passar aqui NÃO garante nota:
 * o teste de correção verifica todas as regras do enunciado.
 *
 * Uso: java TesteFumaca   (sobe despachante, gateway e um motorista no mesmo processo)
 */
public class TesteFumaca {

    static int falhas = 0;

    public static void main(String[] args) throws Exception {
        PrintStream out = new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8);
        PrintStream logs = new PrintStream(new FileOutputStream("log-fumaca.txt"), true, StandardCharsets.UTF_8);
        System.setOut(logs);
        System.setErr(logs);

        Registry reg = LocateRegistry.createRegistry(1099);
        ServidorDespachante srv = new ServidorDespachante();
        reg.rebind("DespachanteCorridas", srv);
        // stub de verdade (lookup no registry local devolveria o próprio objeto, sem RMI)
        ServicoDespachante d = (ServicoDespachante) LocateRegistry.getRegistry("localhost", 1099).lookup("DespachanteCorridas");
        GatewayPassageiros gw = new GatewayPassageiros(d);
        gw.iniciar(5099);

        Cliente p = new Cliente(5099);

        // 1. Sem motoristas: OK e depois EXPIRADA
        String id1 = p.solicitar("SOLICITAR P1 Ana 0 0 0.01 0.01 STANDARD");
        verificar(out, "SOLICITAR responde OK <id>", id1 != null);
        verificar(out, "sem motorista, a corrida expira (EXPIRADA)", id1 != null && p.esperar("EXPIRADA " + id1, 4000));

        // 2. Comando inválido
        int n = p.linhas.size();
        p.enviar("PIZZA GRANDE");
        verificar(out, "comando desconhecido responde ERRO", p.esperarDesde(n, "ERRO", 1000));

        // 3. Ciclo completo com um motorista que aceita sozinho
        Motorista m = new Motorista(d);
        d.registrarMotorista(new InfoMotorista("M1", "Joao", "ABC1234", new Localizacao(0, 0)), m);
        String id2 = p.solicitar("SOLICITAR P1 Ana 0 0 0.01 0.01 VIP");
        verificar(out, "ATRIBUIDA <id> M1", id2 != null && p.esperar("ATRIBUIDA " + id2 + " M1", 2500));
        verificar(out, "INICIADA <id>", id2 != null && p.esperar("INICIADA " + id2, 2000));
        verificar(out, "CONCLUIDA <id>", id2 != null && p.esperar("CONCLUIDA " + id2, 2000));

        // 4. Cancelar a própria corrida antes de iniciar
        m.aceitar = false;
        String id3 = p.solicitar("SOLICITAR P1 Ana 0 0 0.01 0.01 STANDARD");
        Thread.sleep(200);
        p.enviar("CANCELAR " + id3);
        verificar(out, "CANCELAR responde CANCELADA <id>", id3 != null && p.esperar("CANCELADA " + id3, 1500));

        out.println(falhas == 0 ? "\nTodos os itens de fumaça passaram." : "\n" + falhas + " item(ns) falharam. Veja log-fumaca.txt");
        System.exit(0);
    }

    static void verificar(PrintStream out, String nome, boolean ok) {
        if (!ok) falhas++;
        out.println((ok ? "[PASSOU] " : "[FALHOU] ") + nome);
    }

    static class Motorista extends UnicastRemoteObject implements CallbackMotorista {
        final ServicoDespachante d;
        volatile boolean aceitar = true;
        Motorista(ServicoDespachante d) throws RemoteException { this.d = d; }

        public void aoAtribuir(AtribuicaoCorrida a) {
            if (!aceitar) return;
            new Thread(() -> {
                try {
                    Thread.sleep(200);
                    if (d.aceitarAtribuicao(a.getAtribuicaoId())) {
                        Thread.sleep(200);
                        d.iniciarCorrida(a.getAtribuicaoId());
                        Thread.sleep(300);
                        d.concluirCorrida(a.getAtribuicaoId());
                    }
                } catch (Exception ignored) { }
            }).start();
        }

        public void aoCancelar(String atribuicaoId) { }
    }

    static class Cliente {
        final PrintWriter w;
        final List<String> linhas = Collections.synchronizedList(new ArrayList<>());

        Cliente(int porta) throws IOException {
            Socket s = new Socket("localhost", porta);
            w = new PrintWriter(new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8), true);
            BufferedReader r = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
            Thread t = new Thread(() -> {
                try { String l; while ((l = r.readLine()) != null) linhas.add(l.trim()); } catch (IOException ignored) { }
            });
            t.setDaemon(true);
            t.start();
        }

        void enviar(String l) { w.println(l); }

        String solicitar(String comando) throws InterruptedException {
            int n = linhas.size();
            enviar(comando);
            long fim = System.currentTimeMillis() + 1500;
            while (System.currentTimeMillis() < fim) {
                synchronized (linhas) {
                    for (int i = n; i < linhas.size(); i++)
                        if (linhas.get(i).startsWith("OK ")) return linhas.get(i).split("\\s+")[1];
                }
                Thread.sleep(20);
            }
            return null;
        }

        boolean esperar(String prefixo, long ms) throws InterruptedException { return esperarDesde(0, prefixo, ms); }

        boolean esperarDesde(int desde, String prefixo, long ms) throws InterruptedException {
            long fim = System.currentTimeMillis() + ms;
            while (System.currentTimeMillis() < fim) {
                synchronized (linhas) {
                    for (int i = desde; i < linhas.size(); i++) if (linhas.get(i).startsWith(prefixo)) return true;
                }
                Thread.sleep(20);
            }
            return false;
        }
    }
}
