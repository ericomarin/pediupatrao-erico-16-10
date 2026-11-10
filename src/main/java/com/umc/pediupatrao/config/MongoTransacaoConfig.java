package com.umc.pediupatrao.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;

// Habilita transações no MongoDB (requer o MongoDB em modo replica set).
// garante que pedido e auditoria sejam gravados juntos (ou nenhum dos dois).
@Configuration
public class MongoTransacaoConfig {

    @Bean
    MongoTransactionManager transactionManager(MongoDatabaseFactory fabrica) {
        return new MongoTransactionManager(fabrica);
    }
}
