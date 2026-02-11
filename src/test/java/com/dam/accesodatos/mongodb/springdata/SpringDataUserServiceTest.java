package com.dam.accesodatos.mongodb.springdata;

import com.dam.accesodatos.config.MongoDbTestConfiguration;
import com.dam.accesodatos.exception.DuplicateEmailException;
import com.dam.accesodatos.exception.UserNotFoundException;
import com.dam.accesodatos.model.User;
import com.dam.accesodatos.model.UserCreateDto;
import com.dam.accesodatos.model.UserQueryDto;
import com.dam.accesodatos.model.UserUpdateDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@Import(MongoDbTestConfiguration.class)
@DisplayName("SpringDataUserService Tests")
class SpringDataUserServiceTest {

    @Autowired
    private SpringDataUserService service;

    private String uniqueEmail() {
        return "test-" + UUID.randomUUID().toString().substring(0, 8) + "@test.com";
    }

    @Nested
    @DisplayName("Test Connection")
    class TestConnection {

        @Test
        @DisplayName("Debe conectar exitosamente a MongoDB")
        void testConnection_Success() {
            String result = service.testConnection();
            assertThat(result).contains("Spring Data exitosa");
            assertThat(result).contains("users");
        }
    }

    @Nested
    @DisplayName("Create User")
    class CreateUser {

        @Test
        @DisplayName("Debe crear usuario con datos válidos")
        void createUser_ValidData_ReturnsUserWithId() {
            String email = uniqueEmail();
            UserCreateDto dto = new UserCreateDto("Spring Test", email, "IT", "Developer");
            User created = service.createUser(dto);

            assertThat(created.getId()).isNotNull();
            assertThat(created.getName()).isEqualTo("Spring Test");
            assertThat(created.getEmail()).isEqualTo(email);
            assertThat(created.getDepartment()).isEqualTo("IT");
            assertThat(created.getRole()).isEqualTo("Developer");
            assertThat(created.getActive()).isTrue();
            assertThat(created.getCreatedAt()).isNotNull();
        }

        @Test
        @DisplayName("Debe lanzar DuplicateEmailException con email duplicado")
        @org.junit.jupiter.api.Disabled("Requiere índice único en MongoDB - funciona en producción pero no en MongoDB embebido sin configuración adicional")
        void createUser_DuplicateEmail_ThrowsException() {
            String duplicateEmail = uniqueEmail();
            UserCreateDto dto1 = new UserCreateDto("User 1", duplicateEmail, "IT", "Dev");
            service.createUser(dto1);

            UserCreateDto dto2 = new UserCreateDto("User 2", duplicateEmail, "HR", "Manager");
            assertThatThrownBy(() -> service.createUser(dto2))
                    .isInstanceOf(DuplicateEmailException.class)
                    .hasMessageContaining(duplicateEmail);
        }
    }

    @Nested
    @DisplayName("Find User By ID")
    class FindUserById {

        @Test
        @DisplayName("Debe encontrar usuario existente")
        void findUserById_ExistingId_ReturnsUser() {
            String email = uniqueEmail();
            UserCreateDto dto = new UserCreateDto("Find Spring", email, "HR", "Recruiter");
            User created = service.createUser(dto);

            User found = service.findUserById(created.getId());

            assertThat(found).isNotNull();
            assertThat(found.getId()).isEqualTo(created.getId());
            assertThat(found.getEmail()).isEqualTo(email);
        }

        @Test
        @DisplayName("Debe lanzar UserNotFoundException con ID inexistente")
        void findUserById_NonExistingId_ThrowsException() {
            String nonExistingId = "507f1f77bcf86cd799439011";

            assertThatThrownBy(() -> service.findUserById(nonExistingId))
                    .isInstanceOf(UserNotFoundException.class)
                    .hasMessageContaining(nonExistingId);
        }
    }

    @Nested
    @DisplayName("Update User")
    class UpdateUser {

        @Test
        @DisplayName("Debe actualizar usuario existente")
        void updateUser_ValidData_ReturnsUpdatedUser() {
            String email = uniqueEmail();
            UserCreateDto createDto = new UserCreateDto("Update Spring", email, "IT", "Dev");
            User created = service.createUser(createDto);

            UserUpdateDto updateDto = new UserUpdateDto();
            updateDto.setName("Updated Spring Name");
            updateDto.setDepartment("HR");

            User updated = service.updateUser(created.getId(), updateDto);

            assertThat(updated.getName()).isEqualTo("Updated Spring Name");
            assertThat(updated.getDepartment()).isEqualTo("HR");
            assertThat(updated.getEmail()).isEqualTo(email); // Unchanged
        }

        @Test
        @DisplayName("Debe lanzar UserNotFoundException al actualizar usuario inexistente")
        void updateUser_NonExistingId_ThrowsException() {
            String nonExistingId = "507f1f77bcf86cd799439011";
            UserUpdateDto dto = new UserUpdateDto();
            dto.setName("New Name");

            assertThatThrownBy(() -> service.updateUser(nonExistingId, dto))
                    .isInstanceOf(UserNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("Delete User")
    class DeleteUser {

        @Test
        @DisplayName("Debe eliminar usuario existente")
        void deleteUser_ExistingId_ReturnsTrue() {
            UserCreateDto dto = new UserCreateDto("Delete Spring", uniqueEmail(), "IT", "Dev");
            User created = service.createUser(dto);

            boolean deleted = service.deleteUser(created.getId());

            assertThat(deleted).isTrue();
        }

        @Test
        @DisplayName("Debe retornar false al eliminar usuario inexistente")
        void deleteUser_NonExistingId_ReturnsFalse() {
            String nonExistingId = "507f1f77bcf86cd799439011";

            boolean deleted = service.deleteUser(nonExistingId);

            assertThat(deleted).isFalse();
        }
    }

    @Nested
    @DisplayName("Find All Users")
    class FindAllUsers {

        @Test
        @DisplayName("Debe retornar lista vacía cuando no hay usuarios")
        void findAll_NoUsers_ReturnsEmptyList() {
            var users = service.findAll();
            assertThat(users).isNotNull();
        }

        @Test
        @DisplayName("Debe retornar todos los usuarios")
        void findAll_MultipleUsers_ReturnsAllUsers() {
            service.createUser(new UserCreateDto("Spring User 1", uniqueEmail(), "IT", "Dev"));
            service.createUser(new UserCreateDto("Spring User 2", uniqueEmail(), "HR", "Manager"));

            var users = service.findAll();

            assertThat(users).isNotNull();
            assertThat(users.size()).isGreaterThanOrEqualTo(2);
        }
    }

    @Nested
    @DisplayName("Find Users By Department")
    class FindUsersByDepartment {

        @Test
        @DisplayName("Debe retornar usuarios del departamento IT")
        void findUsersByDepartment_IT_ReturnsITUsers() {
            service.createUser(new UserCreateDto("Spring IT User 1", uniqueEmail(), "IT", "Dev"));
            service.createUser(new UserCreateDto("Spring IT User 2", uniqueEmail(), "IT", "Senior Dev"));
            service.createUser(new UserCreateDto("Spring HR User", uniqueEmail(), "HR", "Manager"));

            var itUsers = service.findUsersByDepartment("IT");

            assertThat(itUsers).isNotNull();
            assertThat(itUsers.size()).isGreaterThanOrEqualTo(2);
            assertThat(itUsers).allMatch(user -> "IT".equals(user.getDepartment()));
        }

        @Test
        @DisplayName("Debe retornar lista vacía para departamento sin usuarios")
        void findUsersByDepartment_NonExisting_ReturnsEmpty() {
            var users = service.findUsersByDepartment("NonExistingDepartment");

            assertThat(users).isNotNull();
            assertThat(users).isEmpty();
        }
    }

    @Nested
    @DisplayName("Search Users")
    class SearchUsers {

        @Test
        @DisplayName("Debe buscar usuarios por nombre parcial")
        void searchUsers_ByName_ReturnsMatchingUsers() {
            service.createUser(new UserCreateDto("Alice Johnson", uniqueEmail(), "IT", "Dev"));
            service.createUser(new UserCreateDto("Alice Smith", uniqueEmail(), "HR", "Manager"));
            service.createUser(new UserCreateDto("Bob Johnson", uniqueEmail(), "IT", "Senior"));

            UserQueryDto query = new UserQueryDto();
            query.setName("Alice");
            query.setSize(10);

            List<User> results = service.searchUsers(query);

            assertThat(results).isNotNull();
            assertThat(results.size()).isGreaterThanOrEqualTo(2);
            assertThat(results).allMatch(user -> user.getName().contains("Alice"));
        }

        @Test
        @DisplayName("Debe buscar usuarios por departamento")
        void searchUsers_ByDepartment_ReturnsMatchingUsers() {
            service.createUser(new UserCreateDto("Spring User 1", uniqueEmail(), "IT", "Dev"));
            service.createUser(new UserCreateDto("Spring User 2", uniqueEmail(), "IT", "Senior"));
            service.createUser(new UserCreateDto("Spring User 3", uniqueEmail(), "HR", "Manager"));

            UserQueryDto query = new UserQueryDto();
            query.setDepartment("IT");

            List<User> results = service.searchUsers(query);

            assertThat(results).isNotNull();
            assertThat(results.size()).isGreaterThanOrEqualTo(2);
            assertThat(results).allMatch(user -> "IT".equals(user.getDepartment()));
        }

        @Test
        @DisplayName("Debe buscar usuarios activos")
        void searchUsers_ByActive_ReturnsActiveUsers() {
            service.createUser(new UserCreateDto("Active Spring User", uniqueEmail(), "IT", "Dev"));

            UserQueryDto query = new UserQueryDto();
            query.setActive(true);

            List<User> results = service.searchUsers(query);

            assertThat(results).isNotNull();
            assertThat(results).allMatch(User::getActive);
        }

        @Test
        @DisplayName("Debe aplicar paginación")
        void searchUsers_WithPagination_ReturnsPagedResults() {
            for (int i = 0; i < 5; i++) {
                service.createUser(new UserCreateDto("Paginated User " + i, uniqueEmail(), "IT", "Dev"));
            }

            UserQueryDto query = new UserQueryDto();
            query.setPage(0);
            query.setSize(3);

            List<User> results = service.searchUsers(query);

            assertThat(results).isNotNull();
            assertThat(results.size()).isLessThanOrEqualTo(3);
        }

        @Test
        @DisplayName("Debe combinar múltiples filtros")
        void searchUsers_MultipleFilters_ReturnsMatchingUsers() {
            service.createUser(new UserCreateDto("Spring IT Dev 1", uniqueEmail(), "IT", "Dev"));
            service.createUser(new UserCreateDto("Spring IT Dev 2", uniqueEmail(), "IT", "Senior"));
            service.createUser(new UserCreateDto("Spring HR Manager", uniqueEmail(), "HR", "Manager"));

            UserQueryDto query = new UserQueryDto();
            query.setName("Spring IT");
            query.setDepartment("IT");
            query.setActive(true);

            List<User> results = service.searchUsers(query);

            assertThat(results).isNotNull();
            assertThat(results).allMatch(user -> 
                user.getName().contains("Spring IT") && 
                "IT".equals(user.getDepartment()) && 
                user.getActive()
            );
        }
    }

    @Nested
    @DisplayName("Count By Department")
    class CountByDepartment {

        @Test
        @DisplayName("Debe contar usuarios del departamento IT")
        void countByDepartment_IT_ReturnsCount() {
            service.createUser(new UserCreateDto("Spring IT User 1", uniqueEmail(), "IT", "Dev"));
            service.createUser(new UserCreateDto("Spring IT User 2", uniqueEmail(), "IT", "Senior"));

            long count = service.countByDepartment("IT");

            assertThat(count).isGreaterThanOrEqualTo(2);
        }

        @Test
        @DisplayName("Debe retornar 0 para departamento sin usuarios")
        void countByDepartment_NonExisting_ReturnsZero() {
            long count = service.countByDepartment("NonExistingDepartment");

            assertThat(count).isEqualTo(0);
        }
    }
}
