Regras do Sistema

R1: Um motorista nunca tem duas corridas ao mesmo tempo.
R2: Corrida CANCELADA ou EXPIRADA nunca muda de estado depois.
R3: Corrida em espera é oferecida assim que algum motorista se torna disponível.
R4: Entre corridas em espera, VIP vem antes do STANDARD; com a mesma prioridade, a mais antiga primeiro; O motorista escolhido é o mais proximo da origem(distancia euclidiana)
R5: Oferta nao aceita em 2s é retirada, e esse motorista nao recebe de novo a mesma corrida.
R6: Corrida nao aceita em ate 3s apos a solicitação expira.
R7: So se cancela corrida que ainda nao foi INICIADA,. O motorista que tinha a oferta ou  a corrida é avisado e fica livre.
R8: Cada passageiro recebe so os eventos das proprias corridas, cada evento uma unica vez e o OK antes de qualquer evento daquela corrida. Passageiro so cancela corrida própria.
R9: Nenhum motorista ou passageiro lento pode atrasar o atendimento dos outros.