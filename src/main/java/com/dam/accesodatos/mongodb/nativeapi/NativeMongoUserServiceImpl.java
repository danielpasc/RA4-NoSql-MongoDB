package com.dam.accesodatos.mongodb.nativeapi;

import com.dam.accesodatos.exception.DuplicateEmailException;
import com.dam.accesodatos.exception.InvalidUserIdException;
import com.dam.accesodatos.exception.UserNotFoundException;
import com.dam.accesodatos.model.DepartmentStatsDto;
import com.dam.accesodatos.model.User;
import com.dam.accesodatos.model.UserCreateDto;
import com.dam.accesodatos.model.UserQueryDto;
import com.dam.accesodatos.model.UserUpdateDto;
import com.mongodb.client.*;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.InsertOneResult;
import com.mongodb.client.result.UpdateResult;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * SERVICIO CON API NATIVA DE MONGODB
 * ===================================
 * Este servicio usa el driver nativo de MongoDB (MongoClient, MongoCollection, Document).
 * Es equivalente a usar JDBC puro en el mundo SQL (sin frameworks ORM).
 *
 * COMPARACIÓN CON JDBC:
 * =====================
 * API Nativa MongoDB                      | JDBC Puro
 * --------------------------------------- | ---------------------------------------
 * MongoClient mongoClient                 | Connection conn = DriverManager.getConnection()
 * MongoDatabase database                  | conn.setSchema("database_name")
 * MongoCollection<Document> collection    | PreparedStatement stmt
 * Document doc = new Document()           | No directo, usas setString(), setInt()
 * collection.insertOne(doc)               | stmt.executeUpdate("INSERT INTO...")
 * collection.find(Filters.eq(...))        | stmt.executeQuery("SELECT * WHERE...")
 * Document.get("campo")                   | rs.getString("campo")
 *
 * VENTAJAS API NATIVA MONGODB:
 * - Documentos JSON/BSON son más naturales que construir SQL strings
 * - No necesitas mapear manualmente ResultSet a objetos (solo Document a User)
 * - Índices, agregaciones y búsquedas son más expresivos
 *
 * DESVENTAJAS (vs Spring Data):
 * - Mucho código boilerplate (mapear Document ↔ User manualmente)
 * - Propenso a errores (typos en nombres de campos)
 * - No hay validación en compile-time
 *
 * CUÁNDO USAR API NATIVA:
 * - Necesitas control total sobre las operaciones
 * - Performance crítico (evitar abstracción de Spring Data)
 * - Operaciones bulk complejas
 * - Aprender cómo funciona MongoDB internamente
 */
@Service
public class NativeMongoUserServiceImpl implements NativeMongoUserService {

    private static final Logger log = LoggerFactory.getLogger(NativeMongoUserServiceImpl.class);

    /**
     * MONGOCLIENT: Cliente del driver nativo
     * ======================================
     * Equivalente JDBC:
     * private final Connection connection;
     * 
     * IMPORTANTE:
     * - MongoClient es thread-safe (se puede compartir)
     * - Connection en JDBC NO es thread-safe (necesitas pool como HikariCP)
     */
    private final MongoClient mongoClient;
    private final String databaseName;

    @Autowired
    public NativeMongoUserServiceImpl(MongoClient mongoClient,
                                      @Value("${spring.data.mongodb.database}") String databaseName) {
        this.mongoClient = mongoClient;
        this.databaseName = databaseName;
        log.info("NativeMongoUserService inicializado con base de datos: {}", databaseName);
    }

    /**
     * OBTENER COLECCIÓN (EQUIVALENTE A OBTENER TABLE EN JDBC)
     * ========================================================
     * MongoDB:                                 | JDBC:
     * ---------------------------------------- | ----------------------------------------
     * MongoCollection<Document> collection =   | PreparedStatement stmt = conn
     *   mongoClient.getDatabase("db")          |   .prepareStatement("SELECT * FROM users")
     *   .getCollection("users")                |
     *
     * DIFERENCIAS CLAVE:
     * - En MongoDB la "colección" es como una tabla, pero sin esquema fijo
     * - No necesitas CREATE TABLE, la colección se crea automáticamente al insertar el primer documento
     * - MongoCollection<Document> es typed (Document), en JDBC usas tipos primitivos
     */
    private MongoCollection<Document> getCollection() {
        return mongoClient.getDatabase(databaseName).getCollection("users");
        // Equivalente JDBC no directo - prepararías statement cada vez
    }

    /**
     * EJEMPLO 0: TEST DE CONEXIÓN CON API NATIVA
     * ===========================================
     * Demuestra cómo verificar la conexión a MongoDB y obtener información básica.
     *
     * COMPARACIÓN CON JDBC:
     * =====================
     * MongoDB (API Nativa):
     * ---------------------
     * MongoDatabase db = mongoClient.getDatabase("nombre");
     * db.runCommand(new Document("ping", 1));
     * db.listCollectionNames().into(new ArrayList<>());
     * collection.countDocuments();
     *
     * JDBC (SQL):
     * -----------
     * Connection conn = dataSource.getConnection();
     * Statement stmt = conn.createStatement();
     * ResultSet rs = stmt.executeQuery("SELECT 1");  // Test de conexión
     * DatabaseMetaData meta = conn.getMetaData();
     * ResultSet tables = meta.getTables(null, null, "%", null);  // Listar tablas
     * ResultSet countRs = stmt.executeQuery("SELECT COUNT(*) FROM users");
     *
     * OPERACIONES DEMOSTRADAS:
     * 1. Obtener base de datos: mongoClient.getDatabase(name)
     * 2. Listar colecciones: database.listCollectionNames()
     * 3. Ejecutar comando: database.runCommand(new Document("ping", 1))
     * 4. Contar documentos: collection.countDocuments()
     *
     * EQUIVALENCIAS SQL:
     * - listCollectionNames() → SHOW TABLES
     * - runCommand("ping") → SELECT 1
     * - countDocuments() → SELECT COUNT(*) FROM users
     */
    @Override
    public String testConnection() {
        log.debug("Probando conexión a MongoDB...");
        try {
            MongoDatabase database = mongoClient.getDatabase(databaseName);

            List<String> collections = new ArrayList<>();
            database.listCollectionNames().into(collections);

            Document pingCommand = new Document("ping", 1);
            Document result = database.runCommand(pingCommand);

            long userCount = getCollection().countDocuments();

            String message = String.format("Conexión API Nativa exitosa | BD: %s | Colecciones: %d | Usuarios: %d | Ping: %s",
                    databaseName, collections.size(), userCount, result.get("ok"));
            log.info(message);
            return message;
        } catch (Exception e) {
            log.error("Error al probar conexión: {}", e.getMessage(), e);
            throw new RuntimeException("Error al probar conexión: " + e.getMessage(), e);
        }
    }

    /**
     * EJEMPLO 1: CREAR USUARIO (INSERT)
     * ==================================
     * Demuestra cómo insertar un documento en MongoDB.
     *
     * COMPARACIÓN CON JDBC:
     * =====================
     * MongoDB (API Nativa):
     * ---------------------
     * Document doc = new Document()
     *   .append("name", "Juan")
     *   .append("email", "juan@email.com")
     *   .append("department", "IT");
     * collection.insertOne(doc);
     * ObjectId id = result.getInsertedId();
     *
     * JDBC Puro:
     * ----------
     * String sql = "INSERT INTO users(name, email, department, role, active, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)";
     * PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
     * stmt.setString(1, dto.getName());
     * stmt.setString(2, dto.getEmail());
     * stmt.setString(3, dto.getDepartment());
     * stmt.setString(4, dto.getRole());
     * stmt.setBoolean(5, true);
     * stmt.setTimestamp(6, Timestamp.valueOf(LocalDateTime.now()));
     * stmt.setTimestamp(7, Timestamp.valueOf(LocalDateTime.now()));
     * stmt.executeUpdate();
     * ResultSet rs = stmt.getGeneratedKeys();
     * if (rs.next()) { long id = rs.getLong(1); }
     *
     * VENTAJAS MONGODB:
     * - Documento JSON es más legible que SQL con ?
     * - MongoDB genera ObjectId automáticamente (más robusto que AUTO_INCREMENT)
     * - No necesitas especificar tipos de columna
     * - Puedes insertar campos opcionales dinámicamente
     *
     * VENTAJAS JDBC/SQL:
     * - Validación de esquema en compile-time (catch errores antes)
     * - Constraints de base de datos (NOT NULL, CHECK, etc.)
     */
    @Override
    public User createUser(UserCreateDto dto) {
        log.debug("Creando usuario con email: {}", dto.getEmail());
        try {
            // 1. Obtener colección (equivalente a preparar el INSERT statement)
            MongoCollection<Document> collection = getCollection();

            // 2. Construir documento BSON (equivalente a setear parámetros en PreparedStatement)
            Document doc = new Document()
                    .append("name", dto.getName())           // En JDBC: stmt.setString(1, dto.getName())
                    .append("email", dto.getEmail())         // En JDBC: stmt.setString(2, dto.getEmail())
                    .append("department", dto.getDepartment())
                    .append("role", dto.getRole())
                    .append("active", true)
                    .append("createdAt", new Date())         // MongoDB usa java.util.Date
                    .append("updatedAt", new Date());        // En JDBC: stmt.setTimestamp(6, ...)

            // 3. Insertar documento (equivalente a executeUpdate())
            InsertOneResult result = collection.insertOne(doc);
            
            // 4. Obtener ID generado (MongoDB genera ObjectId automáticamente)
            ObjectId id = result.getInsertedId().asObjectId().getValue();
            // En JDBC: ResultSet keys = stmt.getGeneratedKeys(); keys.getLong(1);

            // 5. Mapear Document a User (en JDBC mapearías ResultSet a User)
            User user = mapDocumentToUser(doc, id.toString());
            log.info("Usuario creado exitosamente con ID: {}", id);
            return user;
        } catch (Exception e) {
            // Manejo de error de clave duplicada (índice único en email)
            if (e.getMessage().contains("duplicate key") || e.getMessage().contains("E11000")) {
                log.warn("Intento de crear usuario con email duplicado: {}", dto.getEmail());
                throw new DuplicateEmailException(dto.getEmail());
                // En JDBC sería: SQLIntegrityConstraintViolationException
            }
            log.error("Error al crear usuario: {}", e.getMessage(), e);
            throw new RuntimeException("Error al crear usuario: " + e.getMessage(), e);
        }
    }

    /**
     * EJEMPLO 2: BUSCAR POR ID (SELECT BY PRIMARY KEY)
     * ================================================
     * Demuestra cómo buscar un documento por su ID (ObjectId).
     *
     * COMPARACIÓN CON JDBC:
     * =====================
     * MongoDB:
     * --------
     * Document doc = collection.find(Filters.eq("_id", new ObjectId(id))).first();
     *
     * JDBC:
     * -----
     * String sql = "SELECT * FROM users WHERE id = ?";
     * PreparedStatement stmt = conn.prepareStatement(sql);
     * stmt.setLong(1, id);
     * ResultSet rs = stmt.executeQuery();
     * if (rs.next()) {
     *     User user = new User();
     *     user.setId(rs.getLong("id"));
     *     user.setName(rs.getString("name"));
     *     // ... mapear todos los campos manualmente
     * }
     *
     * CONCEPTOS CLAVE:
     * - Filters.eq("_id", valor): Crea filtro de igualdad (WHERE id = ?)
     * - first(): Retorna el primer documento o null (como rs.next() en JDBC)
     * - ObjectId: Tipo especial de MongoDB para IDs (24 caracteres hexadecimales)
     *
     * VENTAJAS MONGODB:
     * - ObjectId incluye timestamp de creación
     * - find() retorna Document directamente, no necesitas iterar ResultSet
     * - Filters API es type-safe vs SQL strings propensos a errores
     */
    @Override
    public User findUserById(String id) {
        log.debug("Buscando usuario por ID: {}", id);
        try {
            MongoCollection<Document> collection = getCollection();

            // Buscar por _id (campo especial de MongoDB, equivalente a PRIMARY KEY en SQL)
            Document doc = collection.find(Filters.eq("_id", new ObjectId(id))).first();
            // Equivalente JDBC:
            // SELECT * FROM users WHERE id = ?

            if (doc == null) {
                log.warn("Usuario no encontrado con ID: {}", id);
                throw new UserNotFoundException(id);
            }

            User user = mapDocumentToUser(doc);
            log.debug("Usuario encontrado: {}", user.getEmail());
            return user;
        } catch (UserNotFoundException e) {
            throw e;
        } catch (IllegalArgumentException e) {
            // ObjectId inválido (no es un hex de 24 caracteres)
            log.warn("ID de usuario inválido: {}", id);
            throw new InvalidUserIdException(id, e);
        } catch (Exception e) {
            log.error("Error al buscar usuario: {}", e.getMessage(), e);
            throw new RuntimeException("Error al buscar usuario: " + e.getMessage(), e);
        }
    }

    /**
     * EJEMPLO 3: ACTUALIZAR USUARIO (UPDATE)
     * ======================================
     * Demuestra cómo actualizar campos de un documento existente.
     *
     * COMPARACIÓN CON JDBC:
     * =====================
     * MongoDB:
     * --------
     * Bson update = Updates.combine(
     *     Updates.set("name", "Nuevo Nombre"),
     *     Updates.set("email", "nuevo@email.com"),
     *     Updates.set("updatedAt", new Date())
     * );
     * UpdateResult result = collection.updateOne(Filters.eq("_id", objectId), update);
     * boolean updated = result.getModifiedCount() > 0;
     *
     * JDBC:
     * -----
     * StringBuilder sql = new StringBuilder("UPDATE users SET ");
     * List<Object> params = new ArrayList<>();
     * if (dto.getName() != null) {
     *     sql.append("name = ?, ");
     *     params.add(dto.getName());
     * }
     * if (dto.getEmail() != null) {
     *     sql.append("email = ?, ");
     *     params.add(dto.getEmail());
     * }
     * sql.append("updated_at = ? WHERE id = ?");
     * params.add(Timestamp.valueOf(LocalDateTime.now()));
     * params.add(id);
     * 
     * PreparedStatement stmt = conn.prepareStatement(sql.toString());
     * for (int i = 0; i < params.size(); i++) {
     *     stmt.setObject(i + 1, params.get(i));
     * }
     * int rowsAffected = stmt.executeUpdate();
     *
     * VENTAJAS MONGODB:
     * - Updates.set() es más seguro que construir SQL dinámico
     * - Updates.combine() permite combinar múltiples updates fácilmente
     * - No necesitas manejar índices de parámetros manualmente
     * - updateOne() garantiza que solo se actualice un documento
     *
     * CONCEPTOS CLAVE:
     * - Updates.set(campo, valor): Operador $set de MongoDB
     * - Updates.combine(): Combina múltiples operaciones de update
     * - updateOne(): Actualiza el primer documento que coincida con el filtro
     * - getModifiedCount(): Número de documentos realmente modificados
     */
    @Override
    public User updateUser(String id, UserUpdateDto dto) {
        log.debug("Actualizando usuario con ID: {}", id);
        try {
            MongoCollection<Document> collection = getCollection();

            // Construir updates dinámicamente (solo campos no-null)
            List<Bson> updates = new ArrayList<>();
            if (dto.getName() != null) {
                updates.add(Updates.set("name", dto.getName()));
            }
            if (dto.getEmail() != null) {
                updates.add(Updates.set("email", dto.getEmail()));
            }
            if (dto.getDepartment() != null) {
                updates.add(Updates.set("department", dto.getDepartment()));
            }
            if (dto.getRole() != null) {
                updates.add(Updates.set("role", dto.getRole()));
            }
            if (dto.getActive() != null) {
                updates.add(Updates.set("active", dto.getActive()));
            }
            // Siempre actualizar updatedAt
            updates.add(Updates.set("updatedAt", new Date()));

            // Combinar todos los updates en una sola operación
            Bson updateOperation = Updates.combine(updates);
            // En MongoDB: { $set: { name: "X", email: "Y", updatedAt: Date } }
            // En SQL: UPDATE users SET name = ?, email = ?, updated_at = ? WHERE id = ?

            // Ejecutar update
            UpdateResult result = collection.updateOne(
                    Filters.eq("_id", new ObjectId(id)),  // WHERE id = ?
                    updateOperation                        // SET campo1 = ?, campo2 = ?
            );

            // Verificar que se modificó algo
            if (result.getMatchedCount() == 0) {
                log.warn("Usuario no encontrado para actualizar con ID: {}", id);
                throw new UserNotFoundException(id);
            }

            // Obtener documento actualizado
            User user = findUserById(id);
            log.info("Usuario actualizado exitosamente: {}", id);
            return user;
        } catch (UserNotFoundException | InvalidUserIdException e) {
            throw e;
        } catch (Exception e) {
            if (e.getMessage() != null && (e.getMessage().contains("duplicate key") || e.getMessage().contains("E11000"))) {
                log.warn("Intento de actualizar con email duplicado: {}", dto.getEmail());
                throw new DuplicateEmailException(dto.getEmail());
            }
            log.error("Error al actualizar usuario: {}", e.getMessage(), e);
            throw new RuntimeException("Error al actualizar usuario: " + e.getMessage(), e);
        }
    }

    /**
     * EJEMPLO 4: ELIMINAR USUARIO (DELETE)
     * ====================================
     * Demuestra cómo eliminar un documento de la colección.
     *
     * COMPARACIÓN CON JDBC:
     * =====================
     * MongoDB:
     * --------
     * DeleteResult result = collection.deleteOne(Filters.eq("_id", new ObjectId(id)));
     * boolean deleted = result.getDeletedCount() > 0;
     *
     * JDBC:
     * -----
     * String sql = "DELETE FROM users WHERE id = ?";
     * PreparedStatement stmt = conn.prepareStatement(sql);
     * stmt.setLong(1, id);
     * int rowsAffected = stmt.executeUpdate();
     * boolean deleted = rowsAffected > 0;
     *
     * VENTAJAS MONGODB:
     * - deleteOne() garantiza que solo se elimine un documento
     * - En SQL podrías borrar múltiples filas por error si no usas PRIMARY KEY
     * - MongoDB NO tiene CASCADE DELETE, debes manejar relaciones manualmente
     *
     * CONCEPTOS CLAVE:
     * - deleteOne(): Elimina el primer documento que coincida
     * - deleteMany(): Eliminaría todos los documentos que coincidan
     * - getDeletedCount(): Número de documentos eliminados (0 o 1 con deleteOne)
     *
     * DIFERENCIA CON SQL:
     * - MongoDB no tiene claves foráneas ni CASCADE
     * - Si tienes "pedidos" relacionados con este usuario, NO se borrarán automáticamente
     * - En SQL con FK + ON DELETE CASCADE, las filas relacionadas se borrarían
     */
    @Override
    public boolean deleteUser(String id) {
        log.debug("Eliminando usuario con ID: {}", id);
        try {
            MongoCollection<Document> collection = getCollection();

            // Eliminar documento por _id
            DeleteResult result = collection.deleteOne(Filters.eq("_id", new ObjectId(id)));
            // Equivalente SQL: DELETE FROM users WHERE id = ?

            if (result.getDeletedCount() > 0) {
                log.info("Usuario eliminado exitosamente: {}", id);
                return true;
            } else {
                log.warn("Usuario no encontrado para eliminar: {}", id);
                return false;
            }
        } catch (IllegalArgumentException e) {
            log.warn("ID de usuario inválido para eliminar: {}", id);
            throw new InvalidUserIdException(id, e);
        } catch (Exception e) {
            log.error("Error al eliminar usuario: {}", e.getMessage(), e);
            throw new RuntimeException("Error al eliminar usuario: " + e.getMessage(), e);
        }
    }

    @Override
    public List<User> findAll() {
        // TODO: Implementar findAll() - Los estudiantes deben completar este método
        // PISTAS:
        // 1. Obtener la colección con getCollection()
        // 2. Usar collection.find() sin filtros
        // 3. Iterar con MongoCursor y mapear cada Document a User
        // 4. Retornar la lista de usuarios
        throw new UnsupportedOperationException("TODO: Implementar findAll() - Los estudiantes deben completar este método");
    }

    @Override
    public List<User> findUsersByDepartment(String department) {
        // TODO: Implementar findUsersByDepartment() - Los estudiantes deben completar este método
        // PISTAS:
        // 1. Obtener la colección con getCollection()
        // 2. Usar Filters.eq("department", department)
        // 3. Iterar con MongoCursor y mapear cada Document a User
        // 4. Retornar la lista de usuarios
        throw new UnsupportedOperationException("TODO: Implementar findUsersByDepartment() - Los estudiantes deben completar este método");
    }

    @Override
    public List<User> searchUsers(UserQueryDto query) {
        // TODO: Implementar searchUsers() - Los estudiantes deben completar este método
        // PISTAS:
        // 1. Construir filtros dinámicos con Filters.and()
        // 2. Usar Filters.regex() para búsqueda parcial por nombre
        // 3. Aplicar paginación con skip() y limit()
        // 4. Aplicar ordenamiento con Sorts.ascending() o Sorts.descending()
        throw new UnsupportedOperationException("TODO: Implementar searchUsers() - Los estudiantes deben completar este método");
    }

    @Override
    public long countByDepartment(String department) {
        // TODO: Implementar countByDepartment() - Los estudiantes deben completar este método
        // PISTAS:
        // 1. Obtener la colección con getCollection()
        // 2. Usar collection.countDocuments(Filters.eq("department", department))
        throw new UnsupportedOperationException("TODO: Implementar countByDepartment() - Los estudiantes deben completar este método");
    }

    /**
     * Ejemplo de Aggregation Pipeline: Obtiene estadísticas de usuarios por departamento.
     *
     * Este método demuestra el uso del framework de agregación de MongoDB, que permite:
     * - Agrupar documentos por campo ($group)
     * - Calcular totales ($sum)
     * - Calcular condicionalmente ($cond)
     * - Ordenar resultados ($sort)
     *
     * Pipeline equivalente en MongoDB Shell:
     * db.users.aggregate([
     *   { $group: {
     *       _id: "$department",
     *       totalUsers: { $sum: 1 },
     *       activeUsers: { $sum: { $cond: [{ $eq: ["$active", true] }, 1, 0] } }
     *   }},
     *   { $sort: { totalUsers: -1 } }
     * ])
     */
    @Override
    public List<DepartmentStatsDto> getStatsByDepartment() {
        log.debug("Obteniendo estadísticas por departamento con aggregation pipeline");
        try {
            MongoCollection<Document> collection = getCollection();
            List<DepartmentStatsDto> stats = new ArrayList<>();

            // Pipeline de agregación usando el Aggregates builder
            List<org.bson.conversions.Bson> pipeline = List.of(
                    // Etapa 1: Agrupar por departamento y calcular totales
                    Aggregates.group("$department",
                            Accumulators.sum("totalUsers", 1),
                            Accumulators.sum("activeUsers",
                                    new Document("$cond", List.of(
                                            new Document("$eq", List.of("$active", true)),
                                            1,
                                            0
                                    ))
                            )
                    ),
                    // Etapa 2: Ordenar por total de usuarios descendente
                    Aggregates.sort(Sorts.descending("totalUsers"))
            );

            // Ejecutar el pipeline y mapear resultados
            try (MongoCursor<Document> cursor = collection.aggregate(pipeline).iterator()) {
                while (cursor.hasNext()) {
                    Document doc = cursor.next();
                    DepartmentStatsDto dto = new DepartmentStatsDto(
                            doc.getString("_id"),           // department
                            doc.getInteger("totalUsers"),   // total
                            doc.getInteger("activeUsers")   // active
                    );
                    stats.add(dto);
                }
            }

            log.info("Estadísticas por departamento obtenidas: {} departamentos", stats.size());
            return stats;
        } catch (Exception e) {
            log.error("Error al obtener estadísticas: {}", e.getMessage(), e);
            throw new RuntimeException("Error al obtener estadísticas: " + e.getMessage(), e);
        }
    }

    private User mapDocumentToUser(Document doc) {
        return mapDocumentToUser(doc, doc.getObjectId("_id").toString());
    }

    private User mapDocumentToUser(Document doc, String id) {
        User user = new User();
        user.setId(id);
        user.setName(doc.getString("name"));
        user.setEmail(doc.getString("email"));
        user.setDepartment(doc.getString("department"));
        user.setRole(doc.getString("role"));
        user.setActive(doc.getBoolean("active", true));

        Date createdAt = doc.getDate("createdAt");
        if (createdAt != null) {
            user.setCreatedAt(LocalDateTime.ofInstant(createdAt.toInstant(), ZoneId.systemDefault()));
        }

        Date updatedAt = doc.getDate("updatedAt");
        if (updatedAt != null) {
            user.setUpdatedAt(LocalDateTime.ofInstant(updatedAt.toInstant(), ZoneId.systemDefault()));
        }

        return user;
    }
}
