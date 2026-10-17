package edu.assignment.availability.model;

import jakarta.persistence.*;

@Entity @Table(name = "doctors")
public class Doctor {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private String name;
    @Column(nullable = false) private String specialty;
    protected Doctor() {}
    public Doctor(String name, String specialty) { this.name = name; this.specialty = specialty; }
    public Long getId() { return id; }
    public String getName() { return name; }
    public String getSpecialty() { return specialty; }
}
