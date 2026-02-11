package com.example.demo.model;

public record User(
        Long id,
        String name,
        String email,
        Integer age) {
}
