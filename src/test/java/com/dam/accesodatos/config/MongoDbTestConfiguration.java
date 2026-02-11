package com.dam.accesodatos.config;

import de.bwaldvogel.mongo.MongoServer;
import de.bwaldvogel.mongo.backend.memory.MemoryBackend;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;

/**
 * Configuración de MongoDB mock para tests.
 * Usa mongo-java-server que es una implementación en Java puro sin dependencias nativas.
 */
@TestConfiguration
public class MongoDbTestConfiguration {

    @Bean(destroyMethod = "shutdown")
    public MongoServer mongoServer() {
        MongoServer server = new MongoServer(new MemoryBackend());
        server.bind("localhost", 27017);
        return server;
    }

    @Bean
    public MongoDatabaseFactory mongoDbFactory(MongoServer mongoServer) {
        String connectionString = "mongodb://localhost:27017/testdb";
        return new SimpleMongoClientDatabaseFactory(connectionString);
    }

    @Bean
    public MongoTemplate mongoTemplate(MongoDatabaseFactory mongoDbFactory) {
        return new MongoTemplate(mongoDbFactory);
    }
}
