# Testes Kotlin com Awaitility para validar mensagens no Kafka

## **📚 Estrutura do Projeto**

A estrutura básica do projeto é assim:

```bash
.
├── src
│   ├── main
│   │    └── kotlin
│   │         └── api
│   │              └── KafkaProducerService.kt
│   └── test
│        └── kotlin
│             └── api
│                  └── KafkaApiTest.kt
└── pom.xml (ou build.gradle.kts para Kotlin)
```
Certifique-se de incluir as seguintes dependências no seu pom.xml (Maven) ou build.gradle.kts (Gradle):

```xml
<dependency>
    <groupId>org.apache.kafka</groupId>
    <artifactId>kafka-clients</artifactId>
    <version>3.7.0</version>
</dependency>

<dependency>
    <groupId>org.awaitility</groupId>
    <artifactId>awaitility</artifactId>
    <version>4.2.0</version>
</dependency>

<dependency>
    <groupId>com.fasterxml.jackson.module</groupId>
    <artifactId>jackson-module-kotlin</artifactId>
    <version>2.16.0</version>
</dependency>
```

## **🤔 O Que é o Awaitility?**

O Awaitility é uma biblioteca Java/Kotlin projetada para facilitar a espera por condições assíncronas em testes. Em vez de usar Thread.sleep() (o que é ineficiente), o Awaitility permite esperar de forma mais inteligente até que uma condição seja atendida.

✅ Por que usar Awaitility com Kafka?

- Os consumidores Kafka não recebem mensagens imediatamente (processamento assíncrono).

- Precisamos esperar até que a mensagem seja publicada no tópico e lida pelo consumidor.

## **📊 Exemplo Prático: Validando Transações Bancárias**

Vamos criar um teste que:

1. Publica uma mensagem de uma transação bancária em um tópico Kafka.

1. Consome a mensagem do tópico.

1. Valida se o conteúdo recebido está correto.

**🛠️ O Modelo de Transação**

```kotlin
data class TransacaoBancaria @JsonCreator constructor(
    @JsonProperty("idTransacao") val idTransacao: String,
    @JsonProperty("tipo") val tipo: String,
    @JsonProperty("valor") val valor: Double,
    @JsonProperty("contaOrigem") val contaOrigem: String,
    @JsonProperty("contaDestino") val contaDestino: String,
    @JsonProperty("dataHora") val dataHora: String
) {
    companion object {
        fun criarAleatoria(): TransacaoBancaria {
            val tipos = listOf("TRANSFERENCIA", "PAGAMENTO", "DEPOSITO")
            return TransacaoBancaria(
                idTransacao = UUID.randomUUID().toString(),
                tipo = tipos.random(),
                valor = (100..10000).random().toDouble(),
                contaOrigem = "${(100000..999999).random()}-${(0..9).random()}",
                contaDestino = "${(100000..999999).random()}-${(0..9).random()}",
                dataHora = java.time.LocalDateTime.now().toString()
            )
        }
    }
}
```

**📬 O Teste com Kafka e Awaitility**

```kotlin
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class KafkaApiTest {

    private lateinit var kafkaProducer: KafkaProducerService
    private lateinit var kafkaConsumer: KafkaConsumer<String, String>
    private val topic = "transacoes-bancarias"
    private val objectMapper = ObjectMapper() // Para serializar e desserializar JSON

    @BeforeAll
    fun setup() {
        kafkaProducer = KafkaProducerService(topic)

        // Configuração do consumidor Kafka
        val props = Properties().apply {
            put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092")
            put(ConsumerConfig.GROUP_ID_CONFIG, "grupo-de-teste")
            put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java.name)
            put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java.name)
            put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest") // Lê desde o início do tópico
        }
        kafkaConsumer = KafkaConsumer<String, String>(props)
        kafkaConsumer.subscribe(listOf(topic))
    }

    @AfterAll
    fun tearDown() {
        kafkaProducer.close()
        kafkaConsumer.close()
    }

    @Test
    fun `deve enviar e validar transacao bancaria no Kafka`() {
        val key = UUID.randomUUID().toString()

        // Criação da transação bancária
        val transacao = TransacaoBancaria.criarAleatoria()

        // Serializa a transação em JSON
        val message = objectMapper.writeValueAsString(transacao)

        // Envia a transação para o Kafka
        kafkaProducer.sendMessage(key, message)

        // Aguarda e valida se a mensagem foi recebida no Kafka
        await().atMost(Duration.ofSeconds(30)).untilAsserted {
            val registros = kafkaConsumer.poll(Duration.ofMillis(500))
            assertTrue(
                registros.any {
                    it.key() == key && validaMensagem(it.value(), transacao)
                },
                "A transação não foi encontrada ou está incorreta no Kafka"
            )
        }
    }

    // Função auxiliar para validar a mensagem recebida
    private fun validaMensagem(receivedMessage: String, expected: TransacaoBancaria): Boolean {
        return try {
            val transacaoRecebida = objectMapper.readValue(receivedMessage, TransacaoBancaria::class.java)
            assertEquals(expected.idTransacao, transacaoRecebida.idTransacao, "ID da transação incorreto")
            assertEquals(expected.tipo, transacaoRecebida.tipo, "Tipo de transação incorreto")
            assertEquals(expected.valor, transacaoRecebida.valor, "Valor da transação incorreto")
            assertEquals(expected.contaOrigem, transacaoRecebida.contaOrigem, "Conta de origem incorreta")
            assertEquals(expected.contaDestino, transacaoRecebida.contaDestino, "Conta de destino incorreta")
            assertEquals(expected.dataHora, transacaoRecebida.dataHora, "Data e hora incorretas")
            true
        } catch (e: Exception) {
            println("Erro ao validar mensagem: ${e.message}")
            false
        }
    }
}
```

### **🧐 Explicando o Teste**

1. Produzimos uma mensagem Kafka com `kafkaProducer.sendMessage()`.

![Tela do IntelliJ com teste executado com sucesso](https://dev-to-uploads.s3.amazonaws.com/uploads/articles/z35a3xi7zv9zt4h0ttp4.png)

1. Consumimos com `kafkaConsumer.poll()`.

3. Usamos o `await().untilAsserted` para esperar até a mensagem ser validada.

Se a mensagem não for encontrada ou os dados estiverem incorretos, o teste falha com uma mensagem de erro clara. ✅


![Tela do Docker com o tópico da transação feita pelo teste](https://dev-to-uploads.s3.amazonaws.com/uploads/articles/8tjm5ccqcbpfohoc12js.png)

## **📢 Conclusão**

Testar mensagens Kafka de forma assíncrona é essencial para garantir a integridade do sistema. Usando o Awaitility com KafkaConsumer, conseguimos validar mensagens de forma eficiente.
