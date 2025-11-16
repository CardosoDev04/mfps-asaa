package com.group9.asaa.mfps

import com.group9.asaa.DemoRepo
import com.group9.asaa.MessageObject
import com.group9.asaa.transport.simulateTimeoutCase
import com.group9.asaa.transport.simulateAgvUnavailableCase
import com.group9.asaa.transport.testAgvAvailablePath

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration
import org.springframework.boot.runApplication
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.PropertySource
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@SpringBootApplication(exclude = [DataSourceAutoConfiguration::class])
@ComponentScan("com.group9.asaa")
@PropertySource("classpath:secrets-dev.properties")
class MfpsApplication

fun main(args: Array<String>) {
	runApplication<MfpsApplication>(*args)
    // Optionally run simulations only in IDE
    simulateTimeoutCase()
    simulateAgvUnavailableCase()
    testAgvAvailablePath()
    println("End of simulations")

}


@RestController
@RequestMapping("/mfps")
class MfpsController {
	@GetMapping("/system-status")
	fun status(): ResponseEntity<String> {
		return ResponseEntity.ok().body("Mfps System is running")
	}
}
