package com.dam.accesodatos.config;

import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * JUnit Extension para iniciar MongoDB con Testcontainers.
 * Se ejecuta UNA VEZ antes de todas las clases de test.
 */
public class MongoDbExtension implements BeforeAllCallback {

    private static MongoDBContainer mongoDBContainer;

    static {
        // Configurar Docker antes de que Testcontainers intente detectarlo
        System.setProperty("DOCKER_HOST", "unix:///var/run/docker.sock");
        System.setProperty("testcontainers.reuse.enable", "true");
    }

    @Override
    public void beforeAll(ExtensionContext context) {
        if (mongoDBContainer == null) {
            try {
                // Forzar la configuración de Docker
                System.setProperty("TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE", "/var/run/docker.sock");
                System.setProperty("TESTCONTAINERS_RYUK_DISABLED", "true");
                
                mongoDBContainer = new MongoDBContainer(DockerImageName.parse("mongo:7.0.5"));
                mongoDBContainer.start();
                
                // Configurar la URI para que Spring la use
                String uri = mongoDBContainer.getReplicaSetUrl();
                System.setProperty("spring.data.mongodb.uri", uri);
                System.out.println("✅ MongoDB Testcontainer started: " + uri);
            } catch (Exception e) {
                System.err.println("❌ Error starting Testcontainer: " + e.getMessage());
                e.printStackTrace();
                throw new RuntimeException("Cannot start MongoDB Testcontainer", e);
            }
        }
    }

    public static MongoDBContainer getContainer() {
        return mongoDBContainer;
    }
}
