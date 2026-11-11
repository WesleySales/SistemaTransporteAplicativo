1)
void atenderConexao(Socket socket)
- Atende um passageiro do inicio ao fim da conexao, interpretando os comandos do protocolo e respondend cada um
- Linha malformatada recebe ERRO, e a conexao segue funcionando
- O passageiro so cancela a corrida propria. TEntativa de cancelar corrida de outro passageiro ou inexistente recebe NEGADO (R8).
- Quando o passageiro desconecta as corridas dele seguem a escolha declarada em decisoes.propirities (D3).
- Vários passageiros sao atendidos ao mesmo tempoe um passageiro lento nao atrasa os outros(R9).

2)
void aoMudarEstado(Evento corrida evento)
- Entrega cada evento so ao passageiro dono da corrida uma unica vez, no formato de protocolo (R8).
- O OK de uma corrida sempre chega ao passageiro antes de qualquer ecento dela (R8).
- Evento de corrida cujo passageiro já desconectou não pode derrubar o gateway.