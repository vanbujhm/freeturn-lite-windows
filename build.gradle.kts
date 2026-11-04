plugins {
    application
}

version = "1.0.0"

application {
    mainClass.set("com.freeturn.lite.FreeTurnLiteApp")
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(17)
    options.encoding = "UTF-8"
}
