package grails.plugin

import grails.plugins.*

class QuickSearchGrailsPlugin extends Plugin {

   def version = "0.7.0"

   // the version or versions of Grails the plugin is designed for
   def grailsVersion = "3.1.10 > *"
   // resources that are excluded from plugin packaging
   def pluginExcludes = [
           "grails-app/views/error.gsp"
   ]

   def title = "Quick Search Plugin"
   def author = "Matouš Kučera"
   def authorEmail = "matous.kucera@tado.com"
   def description = """
Search plugin for domain class properties. Lightweight plugin which puts the ability for searching, it adds utility
functions for building the search result into a string format representation sufficient for auto-complete as well as
functions for listing the results based on the search query.
"""

   def documentation = "http://tadodotcom.github.io/grails-quick-search"

   def license = "BSD"

   def organization = [name: "tado GmbH", url: "http://tado.com/"]

   def issueManagement = [system: "Github", url: "https://github.com/tadodotcom/grails-quick-search/issues"]

   def scm = [url: "https://github.com/tadodotcom/grails-quick-search"]

}
