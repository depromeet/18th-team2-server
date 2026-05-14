package com.team2.server.support

import com.team2.server.config.TestcontainersConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import

@SpringBootTest
@Import(TestcontainersConfiguration::class)
abstract class IntegrationTestSupport
