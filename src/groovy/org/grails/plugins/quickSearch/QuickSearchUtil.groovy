package org.grails.plugins.quickSearch

import org.apache.commons.lang.ClassUtils
import org.apache.commons.logging.LogFactory
import org.codehaus.groovy.grails.commons.GrailsDomainClass

import java.text.Normalizer

/**
 * Util class for searching.
 *
 * @author <a href="mailto:matous.kucera@tado.com">Matouš Kučera</a>
 * @since 14.11.13 11:42
 */
class QuickSearchUtil {

   private static final log = LogFactory.getLog(this)
   private static final String TOKENS = ' '
   private static final boolean TOKENIZE_NUMBERS = true
   private static final String TOKENIZE_WRAPPER = '"'

   static getDomainClassProperties(grailsApplication, domainClass, boolean strings = true, boolean numbers = true) {
      def properties = [] + grailsApplication.getDomainClass(domainClass.name).persistentProperties

      if (getConf(grailsApplication).searchIdentifier) {
         properties << grailsApplication.getDomainClass(domainClass.name).identifier
      }

      properties.findAll {
         boolean useProperty = false
         if (strings) {
            useProperty = it.type == String
         }
         if (!useProperty && numbers) {
            useProperty = ClassUtils.isAssignable(it.type, Number, true)
         }
         return useProperty
      }.collect { it.name }
   }

   static getPropertyByDotName(object, String property) {
      property.tokenize('.').inject object, { obj, prop ->
         obj ? obj[prop] : null
      }
   }

   static getPropertyType(grailsApplication, domainClass, property) {
      def properties = property.split("\\.")
      String firstPropertyName = properties ? properties[0] : null
      def grailsDomainClass = domainClass instanceof GrailsDomainClass ? domainClass : grailsApplication.getDomainClass(domainClass.name)
      def firstProperty = grailsDomainClass?.getPropertyByName(firstPropertyName)

      if (firstProperty?.isAssociation()) {
         int startIndex = property.indexOf(".")
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
         firstProperty.type
      }
   }

   static splitQuery(grailsApplication, String query, tokens, tokenizeNumbers, tokenWrapper) {
      def resultQueries = []
      // token wrapper
      def _tokenWrapper = (tokenWrapper != null) ? tokenWrapper :
         (getConf(grailsApplication).tokenWrapper != null ?
            getConf(grailsApplication).tokenWrapper :
            TOKENIZE_WRAPPER)
      if (!_tokenWrapper.isEmpty() && query?.startsWith(_tokenWrapper) && query?.endsWith(_tokenWrapper)
         && query?.size() > 2) {
         resultQueries.add(query.substring(1, query.size() -1 ))
      } else {
         // use tokenizer
         def _tokens = (tokens != null) ? tokens : (getConf(grailsApplication).tokens ?: TOKENS)
         def queries = _tokens ? query?.tokenize(_tokens) : [query]
         // tokenize numbers
         def _tokenizeNumbers = (tokenizeNumbers != null) ? tokenizeNumbers :
            (getConf(grailsApplication).tokenizeNumbers instanceof Boolean ?
               getConf(grailsApplication).tokenizeNumbers : TOKENIZE_NUMBERS)

         if (_tokenizeNumbers) {
            queries.each {
               resultQueries.addAll(it.findAll(/\d+/)) // add numbers
               resultQueries.addAll(it.findAll(/[^\d]+/)) // add strings
            }
         } else {
            resultQueries = queries
         }
      }

      return resultQueries
   }

   static findMatchResults(object, searchProperties, queries) {
      searchProperties.collectEntries { key, dotName ->
         [(key): getPropertyByDotName(object, dotName)]
      }.findAll { key, property ->
         queries.find { query -> matchSearch(property, query)} != null
      }
   }

   static matchSearch(property, query) {
      if (property) {
         if (property instanceof String) {
            def propertyNormalized = Normalizer.normalize(property, Normalizer.Form.NFD).replaceAll("[^\\p{ASCII}]", "").toLowerCase()
            def queryNormalized = Normalizer.normalize(query, Normalizer.Form.NFD).replaceAll("[^\\p{ASCII}]", "").toLowerCase()
            return propertyNormalized.contains(queryNormalized)
         } else if (ClassUtils.isAssignable(property.getClass(), Number, true)) {
            if (query.isNumber())
               try {
                  return property == query.asType(property.getClass())
               }
               catch (NumberFormatException e) {
                  log.warn "Queried string [$query] could not be translated to number."
               }
         } else if (ClassUtils.isAssignable(property.getClass(), Collection)) {
            return property.any{ matchSearch(it, query) }
         } else {
            log.error "Unsupported class type [${property.getClass()}] for quick search, omitting."
         }
      } else {
         return false
      }
   }

	private static ConfigObject getConf(grailsApplication) {
		grailsApplication.config.grails.plugins.quickSearch.search
	}
}
