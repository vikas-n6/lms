package com.lms.events;

import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serializer;

/**
 * Builds Confluent Avro serializers/deserializers wired to a Schema Registry. Deserialization uses
 * the specific reader so events come back as generated {@code SpecificRecord}s. The client-based
 * factories allow unit tests to pass a {@code MockSchemaRegistryClient} with no running registry.
 */
public final class AvroEventSerde {

    private AvroEventSerde() {}

    /** Serializer backed by a Schema Registry at {@code schemaRegistryUrl}. */
    public static Serializer<Object> serializer(
            String schemaRegistryUrl, boolean autoRegisterSchemas) {
        KafkaAvroSerializer serializer = new KafkaAvroSerializer();
        serializer.configure(serializerConfig(schemaRegistryUrl, autoRegisterSchemas), false);
        return serializer;
    }

    /** Serializer backed by an explicit client (used with a mock registry in tests). */
    public static Serializer<Object> serializer(
            SchemaRegistryClient client, boolean autoRegisterSchemas) {
        KafkaAvroSerializer serializer = new KafkaAvroSerializer(client);
        serializer.configure(serializerConfig("mock://unused", autoRegisterSchemas), false);
        return serializer;
    }

    /** Deserializer backed by a Schema Registry at {@code schemaRegistryUrl}. */
    public static Deserializer<Object> deserializer(String schemaRegistryUrl) {
        KafkaAvroDeserializer deserializer = new KafkaAvroDeserializer();
        deserializer.configure(deserializerConfig("mock://unused", schemaRegistryUrl), false);
        return deserializer;
    }

    /** Deserializer backed by an explicit client (used with a mock registry in tests). */
    public static Deserializer<Object> deserializer(SchemaRegistryClient client) {
        KafkaAvroDeserializer deserializer = new KafkaAvroDeserializer(client);
        deserializer.configure(deserializerConfig("mock://unused", null), false);
        return deserializer;
    }

    private static Map<String, Object> serializerConfig(String url, boolean autoRegisterSchemas) {
        Map<String, Object> config = new HashMap<>();
        config.put(AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, url);
        config.put(AbstractKafkaSchemaSerDeConfig.AUTO_REGISTER_SCHEMAS, autoRegisterSchemas);
        return config;
    }

    private static Map<String, Object> deserializerConfig(String mockUrl, String url) {
        Map<String, Object> config = new HashMap<>();
        config.put(
                AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG,
                url == null ? mockUrl : url);
        config.put(KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG, true);
        return config;
    }
}
