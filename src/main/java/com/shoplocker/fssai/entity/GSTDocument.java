package com.shoplocker.fssai.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;


@Entity
@Table(name="gstdoc")
public class GSTDocument {


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;



}