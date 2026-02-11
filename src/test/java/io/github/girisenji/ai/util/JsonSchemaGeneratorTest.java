package io.github.girisenji.ai.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.lang.reflect.Method;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JsonSchemaGeneratorTest {

    private JsonSchemaGenerator generator;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        generator = new JsonSchemaGenerator(objectMapper);
    }

    @Test
    void testGenerateSchemaForSimpleClass() {
        JsonNode schema = generator.generateSchema(SimpleTestClass.class);

        assertThat(schema).isNotNull();
        assertThat(schema.has("type")).isTrue();
    }

    @Test
    void testGenerateSchemaForString() {
        JsonNode schema = generator.generateSchema(String.class);

        assertThat(schema).isNotNull();
        assertThat(schema.get("type").asText()).isEqualTo("string");
    }

    @Test
    void testGenerateSchemaForInteger() {
        JsonNode schema = generator.generateSchema(Integer.class);

        assertThat(schema).isNotNull();
        assertThat(schema.get("type").asText()).isEqualTo("integer");
    }

    @Test
    void testGenerateSchemaForMethodWithRequestBody() throws Exception {
        Method method = TestController.class.getMethod("createUser", User.class);

        JsonNode schema = generator.generateSchemaForMethod(method);

        assertThat(schema).isNotNull();
        assertThat(schema.has("properties")).isTrue();
    }

    @Test
    void testGenerateSchemaForMethodWithRequestParam() throws Exception {
        Method method = TestController.class.getMethod("searchUsers", String.class);

        JsonNode schema = generator.generateSchemaForMethod(method);

        assertThat(schema).isNotNull();
        assertThat(schema.has("properties")).isTrue();
    }

    @Test
    void testGenerateSchemaForMethodWithPathVariable() throws Exception {
        Method method = TestController.class.getMethod("getUserById", Long.class);

        JsonNode schema = generator.generateSchemaForMethod(method);

        assertThat(schema).isNotNull();
        assertThat(schema.has("properties")).isTrue();
    }

    @Test
    void testGenerateSchemaForMethodWithMultipleParams() throws Exception {
        Method method = TestController.class.getMethod("updateUser", Long.class, User.class);

        JsonNode schema = generator.generateSchemaForMethod(method);

        assertThat(schema).isNotNull();
        assertThat(schema.get("properties").size()).isEqualTo(2);
    }

    @Test
    void testGenerateSchemaForMethodWithNoParams() throws Exception {
        Method method = TestController.class.getMethod("getAllUsers");

        JsonNode schema = generator.generateSchemaForMethod(method);

        assertThat(schema).isNotNull();
        assertThat(schema.get("properties").size()).isEqualTo(0);
    }

    @Test
    void testGenerateSchemaForComplexType() {
        JsonNode schema = generator.generateSchema(User.class);

        assertThat(schema).isNotNull();
        assertThat(schema.has("properties")).isTrue();
    }

    @Test
    void testGenerateSchemaForEnum() {
        JsonNode schema = generator.generateSchema(Status.class);

        assertThat(schema).isNotNull();
        assertThat(schema.get("type").asText()).isEqualTo("string");
    }

    @Test
    void testGenerateSchemaForBoolean() {
        JsonNode schema = generator.generateSchema(Boolean.class);

        assertThat(schema).isNotNull();
        assertThat(schema.get("type").asText()).isEqualTo("boolean");
    }

    // Test classes

    static class SimpleTestClass {
        private String name;
        private int age;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getAge() {
            return age;
        }

        public void setAge(int age) {
            this.age = age;
        }
    }

    static class User {
        private Long id;
        private String username;
        private String email;
        private Status status;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public Status getStatus() {
            return status;
        }

        public void setStatus(Status status) {
            this.status = status;
        }
    }

    enum Status {
        ACTIVE, INACTIVE, PENDING
    }

    static class TestController {
        public void getAllUsers() {
        }

        public void getUserById(@PathVariable Long id) {
        }

        public void createUser(@RequestBody User user) {
        }

        public void updateUser(@PathVariable Long id, @RequestBody User user) {
        }

        public void searchUsers(@RequestParam String query) {
        }
    }
}
