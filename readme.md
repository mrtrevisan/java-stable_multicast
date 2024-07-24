## Stable Multicast - Java
#### Middleware que provisiona um multicast estável, feito em Java.
* Trabalho desenvolvido no âmbito da disciplina de Sistemas Distribuídos - 2024
* Universidade Federal de Santa Maria
* Professor Raul Ceretta Nunes

### Autores:
* Mauro Roberto Trevisan (mrtrevisan)
* Ramon Godoy Izidoro (<a href="https://github.com/ramonXXII/">ramonXXII</a>)

### Dependências:
* openjdk 22.0.2

### Como usar:
1. Compile o código via JAVAC
```
javac -d bin **/*.java
```

2. Execute o Cliente em 2 ou mais abas do terminal:
```
java -cp bin Client
```

3. Troque mensagens entre os terminais:  
3.1. Digite sua mensagem e tecle Enter;  
3.2. Em seguida, escolha enviar via Multicast ou individualmente via Unicast para cada membro.