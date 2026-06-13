package com.team2.server.support

import com.team2.server.config.TestcontainersConfiguration
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.Import

@DataJpaTest
@Import(TestcontainersConfiguration::class)
abstract class JpaSliceTestSupport
