create table if not exists collect_execution_locks (
    lock_name    varchar(100) not null,
    owner_token  varchar(64)  not null,
    locked_until datetime     not null,
    acquired_at  datetime     not null,
    created_at   datetime     not null default current_timestamp,
    updated_at   datetime     not null default current_timestamp on update current_timestamp,
    primary key (lock_name),
    key idx_collect_execution_locks_locked_until (locked_until)
) engine=InnoDB default charset=utf8mb4;
