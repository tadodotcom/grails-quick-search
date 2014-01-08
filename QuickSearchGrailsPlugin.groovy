class QuickSearchGrailsPlugin {
   def version = "0.2.5"
   def grailsVersion = "2.0 > *"

   def title = "Quick Search Plugin"
   def author = "Matouš Kučera"
   def authorEmail = "matous.kucera@tado.com"
   def description = """
Search plugin for domain class properties. Lightweight plugin which puts the ability for searching, it adds utility
functions for building the search result into a string format representation sufficient for auto-complete as well as
functions for listing the results based on the search query.
"""

   def documentation = "http://tadodotcom.github.io/grails-quick-search"

   def license = "GPL2"

   def organization = [name: "tado GmbH", url: "http://tado.com/"]

   def issueManagement = [system: "Github", url: "https://github.com/tadodotcom/grails-quick-search/issues"]

   def scm = [url: "https://github.com/tadodotcom/grails-quick-search"]
}
