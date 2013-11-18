package org.grails.plugins.quickSearch

import org.apache.commons.logging.LogFactory
import org.codehaus.groovy.grails.commons.GrailsDomainClass
import org.codehaus.groovy.grails.commons.GrailsDomainClassProperty

/**
 * Util class for searching.
 *
 * @author <a href="mailto:matous.kucera@tado.com">Matouš Kučera</a>
 * @since 14.11.13 11:42
 */
class QuickSearchUtil {

   private static final log = LogFactory.getLog(this)
   private static final TOKENS = ' '
   private static final TOKENIZE_NUMBERS = true

   static getDomainClassProperties(def grailsApplication, def domainClass, def strings = true, def numbers = true) {
      grailsApplication.getDomainClass(domainClass.name).persistentProperties.findAll {
         if (strings)
            return it.getType() == String
         if (numbers)
            return Number.class.isAssignableFrom(it.getType())
      }.collect{it.name}
   }

   static getPropertyByDotName(Object object, String property) {
      property.tokenize('.').inject object, {obj, prop ->
         obj[prop]
      }
   }

   static getPropertyType(def grailsApplication, def domainClass, def property) {
      def properties = property.split("\\.")
      def firstPropertyName = (properties.size() > 0) ? properties[0] : null
      def grailsDomainClass = (domainClass instanceof GrailsDomainClass) ? domainClass : grailsApplication.getDomainClass(domainClass.name)
      def firstProperty = grailsDomainClass?.getPropertyByName(firstPropertyName)

      if (firstProperty?.isAssociation()) {
         def startIndex = property.indexOf(".")
         if (startIndex >= 0) {
            def referencedDomainClass = firstProperty.isEmbedded() ? firstProperty.component : firstProperty.referencedDomainClass
            // recursion
            getPropertyType(grailsApplication, referencedDomainClass, property.substring(startIndex+1, property.length()))
         }
         else {
            log.error "Cannot resolve property of domain class $domainClass by its name $property"
            return null
         }
      }
      else {
         firstProperty.getType()
      }
   }

   static splitQuery(def grailsApplication, def query, def tokens, def tokenizeNumbers) {
      def resultQueries = []
      // use tokenizer
      def _tokens = tokens ?: (grailsApplication.config.grails.plugins.quickSearch.search.tokens ?: TOKENS)
      def queries = query?.tokenize(_tokens)
      // tokenize numbers
      def _tokenizeNumbers = (tokenizeNumbers != null) ?
         tokenizeNumbers :
         ((!grailsApplication.config.grails.plugins.quickSearch.search.tokenizeNumbers?.isEmpty()) ?
            grailsApplication.config.grails.plugins.quickSearch.search.tokenizeNumbers : TOKENIZE_NUMBERS)

      if (_tokenizeNumbers) {
         queries.each {
            resultQueries.addAll(it.findAll(/\d+/)) // add numbers
            resultQueries.addAll(it.findAll(/[^\d]+/)) // add strings
         }
      }
      else {
         resultQueries = queries
      }

      return resultQueries
   }
}
