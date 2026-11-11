# Saída esperada do protocolo (exemplo)

Estas sessões foram **capturadas de uma execução real** da solução do professor, com um passageiro conectado ao gateway por socket (porta 5000) e, a partir do Cenário 3, um motorista `ClienteMotorista` (aceita em 0,5 s, inicia 1 s depois e conclui 3 s depois).

Como ler:
- `>>` é o que o passageiro enviou; `<<` é o que o gateway respondeu ou enviou por conta própria.
- O tempo entre colchetes é contado a partir do início de cada cenário e é **aproximado**.
- Os **ids das corridas mudam** a cada execução.
- O texto depois de `ERRO` é livre. O que importa é a linha começar com `ERRO` e a conexão continuar aberta.

O que deve ser **igual** na sua solução: as palavras do protocolo, a ordem dos campos, um espaço entre eles e uma linha por mensagem.

## Cenário 1: linhas malformadas

```
[ 0.00 s] >> SOLICITAR P001 Ana -12.9714 -38.5014
[ 0.00 s] << ERRO uso: SOLICITAR <id> <nome> <oLat> <oLon> <dLat> <dLon> <VIP|STANDARD>
[ 0.30 s] >> SOLICITAR P001 Ana abc -38.5014 -12.98 -38.51 VIP
[ 0.30 s] << ERRO parametros invalidos
[ 0.60 s] >> CHAMAR TAXI
[ 0.60 s] << ERRO comando desconhecido
```

## Cenário 2: nenhum motorista conectado

```
[ 0.00 s] >> SOLICITAR P002 Bruno -12.9714 -38.5014 -12.9800 -38.5100 STANDARD
[ 0.02 s] << OK 8FFF325B
[ 3.02 s] << EXPIRADA 8FFF325B
```

## Cenário 3: corrida completa, e outro passageiro tentando cancelá-la

Dois passageiros conectados ao mesmo tempo, cada um na sua conexão.

Passageiro A (dono da corrida):
```
[ 0.00 s] >> SOLICITAR P003 Carla -12.9714 -38.5014 -12.9800 -38.5100 VIP
[ 0.01 s] << OK ADBED29F
[ 0.52 s] << ATRIBUIDA ADBED29F M001
[ 1.52 s] << INICIADA ADBED29F
[ 4.53 s] << CONCLUIDA ADBED29F
```

Passageiro B (outra conexão):
```
[ 0.80 s] >> CANCELAR ADBED29F
[ 0.80 s] << NEGADO ADBED29F
```

## Cenário 4: cancelamento antes de o motorista aceitar

```
[ 0.00 s] >> SOLICITAR P004 Diego -12.9714 -38.5014 -12.9800 -38.5100 STANDARD
[ 0.00 s] << OK 66E538CE
[ 0.20 s] >> CANCELAR 66E538CE
[ 0.21 s] << CANCELADA 66E538CE
```

Situações que fazem parte das decisões da dupla (por exemplo, o que acontece quando o passageiro desconecta) não têm exemplo aqui de propósito.
