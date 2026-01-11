package com.dam.accesodatos.mongodb.springdata;

import com.dam.accesodatos.exception.DuplicateEmailException;
import com.dam.accesodatos.exception.UserNotFoundException;
import com.dam.accesodatos.model.User;
import com.dam.accesodatos.model.UserCreateDto;
import com.dam.accesodatos.model.UserQueryDto;
import com.dam.accesodatos.model.UserUpdateDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * SERVICIO CON SPRING DATA MONGODB
 * =================================
 * Este servicio usa Spring Data MongoDB (MongoRepository + MongoTemplate).
 * Es equivalente a usar Spring Data JPA en el mundo SQL.
 *
 * COMPARACIÓN CON SPRING DATA JPA:
 * ================================
 * Spring Data MongoDB                      | Spring Data JPA
 * ---------------------------------------- | ---------------------------------------
 * MongoRepository<User, String>            | JpaRepository<User, Long>
 * MongoTemplate                            | EntityManager / JdbcTemplate
 * Criteria API                             | Criteria API / JPQL
 * Query query = new Query()                | CriteriaQuery query = cb.createQuery()
 * mongoTemplate.find(query, User.class)    | entityManager.createQuery(jpql)
 *
 * VENTAJAS SPRING DATA (vs API Nativa):
 * - Reduce código boilerplate en un 90%
 * - repository.save() maneja INSERT y UPDATE automáticamente
 * - repository.findById() retorna Optional<User> (null-safe)
 * - Query methods generados automáticamente del nombre
 * - No necesitas mapear manualmente Document ↔ User
 *
 * SIMILITUDES CON JPA:
 * - Misma filosofía: abstraer operaciones de base de datos
 * - save() detecta si es INSERT o UPDATE automáticamente
 * - Repository methods con naming conventions
 * - Uso de template (MongoTemplate/EntityManager) para queries complejas
 *
 * DIFERENCIAS CON JPA:
 * - MongoDB no tiene transacciones ACID por defecto (requiere replica sets)
 * - No hay lazy loading (MongoDB carga documentos completos)
 * - No hay @OneToMany, @ManyToOne (usas referencias o documentos embebidos)
 */
@Service
public class SpringDataUserServiceImpl implements SpringDataUserService {

    private static final Logger log = LoggerFactory.getLogger(SpringDataUserServiceImpl.class);

    /**
     * DEPENDENCIAS INYECTADAS
     * =======================
     * userRepository: Para operaciones CRUD simples (findAll, save, delete)
     * mongoTemplate: Para queries complejas y dinámicas (Criteria API)
     *
     * Equivalente JPA:
     * private final UserRepository userRepository;  // JpaRepository
     * private final EntityManager entityManager;     // Para queries JPQL complejas
     */
    private final UserRepository userRepository;
    private final MongoTemplate mongoTemplate;

    @Autowired
    public SpringDataUserServiceImpl(UserRepository userRepository, MongoTemplate mongoTemplate) {
        this.userRepository = userRepository;
        this.mongoTemplate = mongoTemplate;
        log.info("SpringDataUserService inicializado");
    }

    @Override
    public String testConnection() {
        log.debug("Probando conexión a MongoDB con Spring Data...");
        try {
            long count = mongoTemplate.count(new Query(), User.class);
            boolean collectionExists = mongoTemplate.collectionExists(User.class);
            String collectionName = mongoTemplate.getCollectionName(User.class);

            String message = String.format("Conexión Spring Data exitosa | Colección: %s | Existe: %s | Usuarios: %d",
                    collectionName, collectionExists, count);
            log.info(message);
            return message;
        } catch (Exception e) {
            log.error("Error al probar conexión: {}", e.getMessage(), e);
            throw new RuntimeException("Error al probar conexión: " + e.getMessage(), e);
        }
    }

    /**
     * EJEMPLO 1: CREAR USUARIO CON SPRING DATA
     * ========================================
     * Demuestra cómo Spring Data MongoDB simplifica el INSERT.
     *
     * COMPARACIÓN CON JPA:
     * ====================
     * Spring Data MongoDB:
     * --------------------
     * User user = new User(name, email, dept, role);
     * User saved = userRepository.save(user);
     * // MongoDB genera ObjectId automáticamente
     *
     * Spring Data JPA:
     * ----------------
     * User user = new User(name, email, dept, role);
     * User saved = userRepository.save(user);
     * // Base de datos genera ID con AUTO_INCREMENT
     *
     * VENTAJAS vs API NATIVA:
     * - No necesitas crear Document manualmente
     * - No necesitas mapear de Document a User
     * - save() detecta si es INSERT o UPDATE automáticamente
     * - Spring Data maneja ObjectId generation transparentemente
     *
     * SIMILITUDES CON JPA:
     * - Mismo método save() para INSERT y UPDATE
     * - Retorna la entidad con el ID generado
     * - Manejo de excepciones de clave duplicada similar
     */
    @Override
    public User createUser(UserCreateDto dto) {
        log.debug("Creando usuario con email: {}", dto.getEmail());
        try {
            // Crear objeto User (no Document como en API nativa)
            User user = new User(dto.getName(), dto.getEmail(), dto.getDepartment(), dto.getRole());
            user.setActive(true);
            user.setCreatedAt(LocalDateTime.now());
            user.setUpdatedAt(LocalDateTime.now());

            // save() inserta el documento y retorna el User con ID generado
            User savedUser = userRepository.save(user);
            // En JPA sería idéntico: entityManager.persist(user) o repository.save(user)
            
            log.info("Usuario creado exitosamente con ID: {}", savedUser.getId());
            return savedUser;
        } catch (Exception e) {
            if (e.getMessage().contains("duplicate key") || e.getMessage().contains("E11000")) {
                log.warn("Intento de crear usuario con email duplicado: {}", dto.getEmail());
                throw new DuplicateEmailException(dto.getEmail());
                // En JPA sería: DataIntegrityViolationException
            }
            log.error("Error al crear usuario: {}", e.getMessage(), e);
            throw new RuntimeException("Error al crear usuario: " + e.getMessage(), e);
        }
    }

    /**
     * EJEMPLO 2: BUSCAR USUARIO POR ID CON SPRING DATA
     * =================================================
     * Demuestra cómo findById() simplifica las búsquedas con Optional<T>.
     *
     * COMPARACIÓN CON JPA:
     * ====================
     * Spring Data MongoDB:
     * --------------------
     * Optional<User> opt = userRepository.findById(id);
     * User user = opt.orElseThrow(() -> new UserNotFoundException(id));
     *
     * Spring Data JPA:
     * ----------------
     * Optional<User> opt = userRepository.findById(id);
     * User user = opt.orElseThrow(() -> new UserNotFoundException(id));
     *
     * VENTAJAS vs API NATIVA:
     * - No necesitas convertir String a ObjectId manualmente
     * - No necesitas Filters.eq("_id", objectId)
     * - No necesitas find().first()
     * - No necesitas mapear Document a User
     * - Optional<T> es null-safe (evita NullPointerException)
     *
     * SIMILITUDES CON JPA:
     * - Método idéntico: findById() retorna Optional<T>
     * - Patrón idéntico: orElse(), orElseThrow()
     * - Excepción personalizada cuando no existe
     *
     * COMPARACIÓN SQL:
     * SELECT * FROM users WHERE id = ? LIMIT 1
     */
    @Override
    public User findUserById(String id) {
        log.debug("Buscando usuario por ID: {}", id);
        User user = userRepository.findById(id).orElse(null);
        if (user == null) {
            log.warn("Usuario no encontrado con ID: {}", id);
            throw new UserNotFoundException(id);
        }
        log.debug("Usuario encontrado: {}", user.getEmail());
        return user;
    }

    /**
     * EJEMPLO 3: ACTUALIZAR USUARIO CON SPRING DATA
     * =============================================
     * Demuestra el patrón "load-modify-save" de Spring Data.
     *
     * COMPARACIÓN CON JPA:
     * ====================
     * Spring Data MongoDB:
     * --------------------
     * User user = userRepository.findById(id).orElseThrow();
     * user.setName("Nuevo nombre");
     * userRepository.save(user);  // Spring detecta que es UPDATE
     *
     * Spring Data JPA:
     * ----------------
     * User user = userRepository.findById(id).orElseThrow();
     * user.setName("Nuevo nombre");
     * userRepository.save(user);  // Hibernate detecta dirty checking y hace UPDATE
     *
     * VENTAJAS vs API NATIVA:
     * - No necesitas construir Updates.set() manualmente
     * - No necesitas updateOne() con filtros
     * - save() detecta automáticamente si es INSERT (id null) o UPDATE (id presente)
     * - Código más limpio y orientado a objetos
     *
     * SIMILITUDES CON JPA:
     * - Patrón idéntico: load → modify → save
     * - save() es suficiente, no necesitas update() explícito
     * - @LastModifiedDate se actualiza automáticamente
     *
     * DIFERENCIA CON JPA:
     * - JPA con Hibernate hace dirty checking en memoria (solo actualiza campos modificados)
     * - MongoDB actualiza el documento completo (o necesitas @Field con Updates.set manual)
     */
    @Override
    public User updateUser(String id, UserUpdateDto dto) {
        log.debug("Actualizando usuario con ID: {}", id);
        try {
            // 1. Buscar usuario existente (load)
            User user = userRepository.findById(id)
                    .orElseThrow(() -> {
                        log.warn("Usuario no encontrado para actualizar con ID: {}", id);
                        return new UserNotFoundException(id);
                    });
            // En JPA sería idéntico: userRepository.findById(id).orElseThrow()

            // 2. Modificar campos (modify)
            dto.applyTo(user);  // Aplica solo los campos no-null del DTO
            user.setUpdatedAt(LocalDateTime.now());

            // 3. Guardar cambios (save) - Spring Data detecta que es UPDATE por el ID presente
            User updatedUser = userRepository.save(user);
            // En JPA: Hibernate detecta dirty state y ejecuta UPDATE automáticamente
            // En MongoDB: save() reemplaza el documento completo
            
            log.info("Usuario actualizado exitosamente: {}", id);
            return updatedUser;
        } catch (UserNotFoundException e) {
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
     * EJEMPLO 4: ELIMINAR USUARIO CON SPRING DATA
     * ============================================
     * Demuestra cómo Spring Data simplifica las eliminaciones.
     *
     * COMPARACIÓN CON JPA:
     * ====================
     * Spring Data MongoDB:
     * --------------------
     * if (userRepository.existsById(id)) {
     *     userRepository.deleteById(id);
     *     return true;
     * }
     * return false;
     *
     * Spring Data JPA:
     * ----------------
     * if (userRepository.existsById(id)) {
     *     userRepository.deleteById(id);
     *     return true;
     * }
     * return false;
     *
     * VENTAJAS vs API NATIVA:
     * - No necesitas convertir String a ObjectId
     * - No necesitas Filters.eq("_id", objectId)
     * - No necesitas collection.deleteOne()
     * - No necesitas verificar getDeletedCount()
     * - existsById() + deleteById() es más legible
     *
     * SIMILITUDES CON JPA:
     * - Métodos idénticos: existsById(), deleteById()
     * - Lógica idéntica: verificar existencia antes de eliminar
     * - Retorna boolean indicando éxito
     *
     * COMPARACIÓN SQL:
     * DELETE FROM users WHERE id = ?
     *
     * ALTERNATIVA (sin verificación previa):
     * userRepository.deleteById(id);  // Lanza excepción si no existe
     */
    @Override
    public boolean deleteUser(String id) {
        log.debug("Eliminando usuario con ID: {}", id);
        if (!userRepository.existsById(id)) {
            log.warn("Usuario no encontrado para eliminar: {}", id);
            return false;
        }
        userRepository.deleteById(id);
        log.info("Usuario eliminado exitosamente: {}", id);
        return true;
    }

    @Override
    public List<User> findAll() {
        // TODO: Implementar findAll() - Los estudiantes deben completar este método
        // PISTAS:
        // 1. Usar userRepository.findAll()
        // 2. Es una sola línea de código
        throw new UnsupportedOperationException("TODO: Implementar findAll() - Los estudiantes deben completar este método");
    }

    @Override
    public List<User> findUsersByDepartment(String department) {
        // TODO: Implementar findUsersByDepartment() - Los estudiantes deben completar este método
        // PISTAS:
        // 1. Usar userRepository.findByDepartment(department)
        // 2. El método ya está definido en UserRepository
        throw new UnsupportedOperationException("TODO: Implementar findUsersByDepartment() - Los estudiantes deben completar este método");
    }

    @Override
    public List<User> searchUsers(UserQueryDto query) {
        // TODO: Implementar searchUsers() - Los estudiantes deben completar este método
        // PISTAS:
        // 1. Usar mongoTemplate con Query y Criteria
        // 2. Construir criterios dinámicos: Criteria.where("campo").is(valor)
        // 3. Para búsqueda parcial: Criteria.where("name").regex(query.getName(), "i")
        // 4. Aplicar paginación con query.skip() y query.limit()
        // 5. Aplicar ordenamiento con query.with(Sort.by(...))
        throw new UnsupportedOperationException("TODO: Implementar searchUsers() - Los estudiantes deben completar este método");
    }

    @Override
    public long countByDepartment(String department) {
        // TODO: Implementar countByDepartment() - Los estudiantes deben completar este método
        // PISTAS:
        // 1. Usar userRepository.countByDepartment(department)
        // 2. El método ya está definido en UserRepository
        throw new UnsupportedOperationException("TODO: Implementar countByDepartment() - Los estudiantes deben completar este método");
    }
}
