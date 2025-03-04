package com.exemplo.api

import api.KafkaProducerService
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.*
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper
import java.time.Duration
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Representa uma transação bancária
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
