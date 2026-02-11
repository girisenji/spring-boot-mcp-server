package com.example.demo.service;

import com.example.demo.model.User;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class UserService {

    private final Map<Long, User> users = new ConcurrentHashMap<>();
    private final AtomicLong idCounter = new AtomicLong();

    public UserService() {
        // Initialize with some sample data
        create(new User(null, "John Doe", "john@example.com", 30));
        create(new User(null, "Jane Smith", "jane@example.com", 25));
    }

    public List<User> findAll() {
        return new ArrayList<>(users.values());
    }

    public User findById(Long id) {
        return users.get(id);
    }

    public User create(User user) {
        Long id = user.id() != null ? user.id() : idCounter.incrementAndGet();
        User newUser = new User(id, user.name(), user.email(), user.age());
        users.put(id, newUser);
        return newUser;
    }

    public void delete(Long id) {
        users.remove(id);
    }
}
