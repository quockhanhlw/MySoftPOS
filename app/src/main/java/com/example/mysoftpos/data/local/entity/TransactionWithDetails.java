package com.example.mysoftpos.data.local.entity;

import androidx.room.Embedded;
import androidx.room.Relation;

public class TransactionWithDetails {
    @Embedded
    public TransactionEntity transaction;

    @Relation(parentColumn = "card_id", entityColumn = "id")
    public CardEntity card;

    @Relation(parentColumn = "user_id", entityColumn = "id")
    public UserEntity user;

    @Relation(parentColumn = "terminal_id", entityColumn = "id")
    public TerminalEntity terminal;
}
