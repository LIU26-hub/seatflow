package com.seatflow.security;

import com.seatflow.user.UserRole;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;


@Entity
@Table(name = "app_user")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class AppUser {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name ="username",nullable = false,length = 64)
    private String username;

    @Column(name ="password_hash",nullable = false,length = 100)
    private String passwordHash;

    @Column(name ="display_name",nullable = false,length = 64)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(name ="role",nullable = false,length = 32)
    private UserRole role = UserRole.PARTICIPANT;

    @CreatedDate
    @Column(name ="created_at",nullable = false,updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name ="updated_at",nullable = false)
       private LocalDateTime updatedAt;

    public CurrentUser toCurrentUser() {
        return new CurrentUser(id, username,  role);
    }
}
