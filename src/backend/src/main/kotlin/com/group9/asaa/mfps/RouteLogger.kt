package com.group9.asaa.mfps

import org.slf4j.LoggerFactory
import org.springframework.boot.CommandLineRunner
import org.springframework.context.ApplicationContext
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@Component
class RouteLogger(private val applicationContext: ApplicationContext) : CommandLineRunner {
    private val logger = LoggerFactory.getLogger(RouteLogger::class.java)

    override fun run(vararg args: String?) {
        val requestMappings = applicationContext.getBeansWithAnnotation(RestController::class.java)
        requestMappings.forEach { (name, bean) ->
            val methods = bean::class.java.methods.filter { it.isAnnotationPresent(GetMapping::class.java) }
            methods.forEach { method ->
                val mapping = method.getAnnotation(GetMapping::class.java)
                logger.info("Available route: ${mapping.value.joinToString(", ")}")
            }
        }
    }
}
