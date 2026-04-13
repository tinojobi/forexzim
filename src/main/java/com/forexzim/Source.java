package com.forexzim;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Entity
@Table(name = "sources")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Source {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100, unique = true)
    private String name;

    @Column(length = 50)
    private String type; // 'official', 'bank', 'parallel', 'mobile_money'

    @Column(length = 500)
    private String url;

    @Column(nullable = false, columnDefinition = "boolean default true")
    private Boolean active = true;
}