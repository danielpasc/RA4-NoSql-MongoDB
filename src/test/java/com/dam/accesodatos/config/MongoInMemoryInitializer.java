package com.dam.accesodatos.config;

import de.bwaldvogel.mongo.MongoServer;
import de.bwaldvogel.mongo.backend.memory.MemoryBackend;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.boot.test.util.TestPropertyValues;

import java.net.InetSocketAddress;

/**
 * Arranca un MongoDB "en memoria" (wire-protocol) 100% Java para tests.
 * Ventaja: no requiere Docker ni binarios nativos (Flapdoodle), por lo que funciona en cualquier máquina.
 */
public class MongoInMemoryInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private static MongoServer server;
    private static InetSocketAddress address;

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        if (server == null) {
            server = new MongoServer(new MemoryBackend());
            address = server.bind(); // puerto aleatorio
        }

        String host = "localhost";
        int port = address.getPort();

        String uri = String.format("mongodb://%s:%d/pedagogico_db", host, port);

        TestPropertyValues.of(
                "spring.data.mongodb.uri=" + uri,
                "spring.data.mongodb.database=pedagogico_db",
                "spring.data.mongodb.host=" + host,
                "spring.data.mongodb.port=" + port
        ).applyTo(applicationContext);
    }
}
