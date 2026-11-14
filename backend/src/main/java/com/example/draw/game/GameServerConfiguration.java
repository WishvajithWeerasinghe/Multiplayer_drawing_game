package com.example.draw.game;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GameServerConfiguration {

    @Bean(initMethod = "start", destroyMethod = "close")
    public GameServer gameServer(
            @Value("${game.port:8080}") int gamePort,
            @Value("${server.port:8081}") int appPort) {
        return new GameServer(gamePort, appPort);
    }
}
