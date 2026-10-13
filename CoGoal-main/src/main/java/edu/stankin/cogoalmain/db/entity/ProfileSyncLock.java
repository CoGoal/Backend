package edu.stankin.cogoalmain.db.entity;
import jakarta.persistence.*;

/** A fixed set of rows serializes creation as well as updates across main instances. */
@Entity @Table(name = "profile_sync_locks")
public class ProfileSyncLock { @Id private Integer id; }
