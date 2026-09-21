package com.snaphere.api.auth;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="refresh_tokens") public class RefreshToken {
 @Id private UUID id; @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="user_id",nullable=false) private User user; @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="device_id",nullable=false) private UserDevice device;
 @Column(name="token_hash",nullable=false,unique=true,length=64) private String tokenHash; @Column(name="expires_at",nullable=false) private Instant expiresAt; @Column(name="revoked_at") private Instant revokedAt; @Column(name="created_at",nullable=false) private Instant createdAt;
 protected RefreshToken(){} public static RefreshToken issue(User u,UserDevice d,String hash,Instant expiresAt){RefreshToken t=new RefreshToken();t.id=UUID.randomUUID();t.user=u;t.device=d;t.tokenHash=hash;t.expiresAt=expiresAt;t.createdAt=Instant.now();return t;} public User getUser(){return user;} public UserDevice getDevice(){return device;} public Instant getExpiresAt(){return expiresAt;} public boolean isRevoked(){return revokedAt!=null;} public void revoke(){if(revokedAt==null)revokedAt=Instant.now();}
}
