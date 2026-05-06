package com.team2.server.config

import org.hibernate.dialect.H2Dialect

class NoCheckH2Dialect : H2Dialect() {
    override fun supportsColumnCheck(): Boolean = false

    override fun supportsTableCheck(): Boolean = false
}
