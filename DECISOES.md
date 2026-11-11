# Decisões da dupla

**Integrantes: João Meneses e Wesley Sales**

Responda cada item em no máximo 6 linhas e cite onde a decisão está no código (`Classe.metodo`).
A resposta precisa descrever o que o **seu** código faz. Resposta genérica ou que o código não confirma vale zero.

## D1. Exclusão mútua
O que o seu código faz segurando `trava` e o que faz fora dela? Por quê?

Com a `trava`: toda leitura e escrita de `motoristas`, `corridas`, `atribuicoes` e da fila `espera`, incluindo a escolha de
qual corrida vai para qual motorista (`ServidorDespachante.casar`, `ofertar`, `retirarAtribuicao`) e as checagens de
`aceitarAtribuicao`, `cancelarCorrida`, `expirarOferta` e `expirarCorrida`. Fora dela: toda chamada remota. Ofertas e avisos
ao motorista são apenas encadeados sob a trava e executados por threads do `executor` (`avisarMotorista`); eventos ao gateway
saem na thread chamadora depois do unlock (`publicar`). Motivo: uma chamada RMI pode demorar; feita com a trava, um motorista
ou gateway lento pararia o despachante inteiro (R9), e um motorista que aceitasse de dentro do callback travaria o sistema.

## D2. Fila de espera
Que estrutura vocês usaram e como ela garante a ordem da R4? Uma corrida STANDARD pode esperar para sempre? Justifique.

Um `TreeSet<Busca>` (`ServidorDespachante.espera`) com o comparador `ORDEM_R4`: prioridade (VIP antes), depois o timestamp
da solicitação, depois um número de sequência de chegada para desempate. `casar` percorre o conjunto nessa ordem e dá a cada
corrida o motorista livre mais próximo da origem (`maisProximo`) que ainda não a deixou expirar. Corrida que volta de uma
oferta expirada (`expirarOferta`) reentra com a antiguidade original. Não há envelhecimento: VIP sempre passa na frente.
Mesmo assim a STANDARD não espera para sempre: `expirarCorrida` a encerra como EXPIRADA 3 s após a solicitação (R6). Ela
pode expirar sem ser atendida, mas nunca fica presa na fila.

## D3. Passageiro desconectou
O passageiro fechou a conexão com uma corrida ainda não iniciada. O que acontece com a corrida e por quê?
(Declare a mesma escolha em `decisoes.properties`.)

CANCELAR. Quando a leitura do socket termina, `GatewayPassageiros.atenderConexao` chama `aoDesconectar`, que chama
`cancelarCorrida` para cada corrida daquela conexão ainda não encerrada. O despachante cancela as PENDENTES, OFERTADAS e
ATRIBUIDAS, avisando e liberando o motorista (R7). Para a já INICIADA ele retorna false: ela segue até o fim e os eventos
dela são descartados em `GatewayPassageiros.entregar`, porque a conexão dona está fechada. Motivo: o protocolo não tem
reconexão, então ninguém mais receberia os eventos nem poderia cancelar a corrida; mantê-la prenderia um motorista a um
passageiro que já saiu.

## D4. Evento antes do OK
Um evento de uma corrida pode chegar ao gateway antes de o gateway ter enviado o `OK` dela? Se sim, como o seu código garante a R8?

Sim: o motorista pode aceitar antes de `solicitarCorrida` retornar ao gateway, que até então nem conhece o corridaId. Em
`GatewayPassageiros.aoMudarEstado`, evento de corrida ainda sem dono vai para o buffer `adiantados` (`guardarAdiantado`).
Quando a chamada retorna, `GatewayPassageiros.solicitar` faz, num único bloco `synchronized (estado)`: registra o dono em
`donos`, enfileira o `OK` e só então descarrega os eventos guardados (`entregar`). `aoMudarEstado` usa o mesmo monitor, então
um evento ou chega antes do bloco (fica no buffer e sai depois do OK) ou chega depois (já encontra o dono e entra na fila
atrás do OK). A fila de saída de cada conexão (`Conexao.enviar`) mantém essa ordem até o socket.

## D5. Aceite atrasado
Um motorista chama `aceitarAtribuicao` depois que a oferta dele expirou ou a corrida foi cancelada. O que acontece e onde isso é decidido?

Retorna false e nada muda. Quem decide é `ServidorDespachante.aceitarAtribuicao`, com a `trava`: o aceite só vale se a
atribuição ainda está em `atribuicoes`, a corrida está OFERTADA com esse mesmo atribuicaoId, o motorista aponta para ela e,
pelo relógio, não passaram 2 s da oferta nem 3 s da solicitação. Oferta expirada e corrida cancelada ou expirada já tiveram
a atribuição removida do mapa por `retirarAtribuicao` (chamado por `expirarOferta`, `expirarCorrida` e `cancelarCorrida`),
então o aceite atrasado não encontra nada. A conferência pelo relógio cobre o caso em que o prazo venceu mas o temporizador
ainda não rodou: o aceite é recusado e o temporizador retira a oferta em seguida.
