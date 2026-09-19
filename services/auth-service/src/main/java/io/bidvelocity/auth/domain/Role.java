package io.bidvelocity.auth.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "roles")
public class Role {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true)
    private String name;

    protected Role() {}
    public Role(String name) { this.name = name; }

    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String v) { name = v; }
}
