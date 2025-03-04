package api

import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringSerializer
import java.util.*

class KafkaProducerService(private val topic: String) {

    private val producer: KafkaProducer<String, String>

    init {
        val props = Properties().apply {
            put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092")
            put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java.name)
            put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java.name)
            put(ProducerConfig.ACKS_CONFIG, "all") // Garantir que a mensagem foi recebida
        }
        producer = KafkaProducer(props)
    }

    fun sendMessage(key: String, message: String) {
        println("Enviando mensagem para o tópico '$topic': $message")
        val record = ProducerRecord(topic, key, message)
        producer.send(record) { metadata, exception ->
            if (exception != null) {
                println("Erro ao enviar mensagem: ${exception.message}")
            } else {
                println("Mensagem enviada para ${metadata.topic()} [offset: ${metadata.offset()}]")
            }
        }
    }

    fun close() {
        producer.close()
    }
}
