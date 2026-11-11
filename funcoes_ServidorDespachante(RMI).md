Metodos a implementar no Servidor Despachante (RMI):

1. String solicitarCorrida(InfoPassageiro passageiro, Localizacao origem, Localizacao destino, Prioridade prioridade)
    - Registra a corrida e devolve o identificador dela na hora, sem esperar por motorista. 
    - A partir dai, a corrida disputa um motorisata seguindo a ordem da R4.
    - Oferta nao aceita em 2s é retirada e a corrida volta a procurar motorista sem repetir quem deixou a oferta expirar(R5).
    - Se nenhum motorista aceitar a corrida em 3s, ela expira e o gateway é avisado (R6).
    - Um motorista que demore a responder à oferta nao pode atrasar este retorno nem o atendimento de outras corridas (R9).

2. boolean aceitarAtribuicao(String atribuicaoId)
    - Retorna true apenas se aquela oferta ainda vale pra aquele motorista. Em qualquero outro caso retorna false e nada muda.
    - Ao aceitar a corrida fica ATRIBUIDA e o gateway é avisado
    - Aceite qe chega depois da oferta ter sido retirada ou da corrida ter sido cancelada ou expirada, nunca recebe a corrida (R2).

3. boolean cancelarCorrida(String corridaId)
    - Cancela apenas corridas ainda ano iniciadas. nos demais casos retorna false sem alterar nada (R7).
    - O motorista que tinha a oferta ou a corrida é avisado e volta a ficar livre pra outras corridas
    - O passageiro fica sabendo qfo cancelamento uma unica vez (R8).

4. void motoristaFicouDisponivel(String motoristaId)
    - É chamado pelo codigo pronto quando um motorista se registra concli uma corrida ou volta de OFFLINE.
    - Se houver corridas esperadno, o motorista qe ficou livre é aproveitado imediatamente, respeitando a ordem da R4 (R3).