grails.project.work.dir = 'target'

grails.project.dependency.resolver = "maven"
grails.project.dependency.resolution = {
   inherits "global"
   log "warn"

   repositories {
      grailsCentral()
      mavenLocal()
      mavenCentral()
   }

   dependencies {
      compile 'org.apache.commons:commons-lang3:3.3.2'
   }

   plugins {
      build(":release:3.1.2", ":rest-client-builder:2.1.1") {
         export = false
      }

      compile(":hibernate:3.6.10.19") {
         export = false
      }
   }
}
