package org.grails.plugins.quickSearch

import groovy.text.SimpleTemplateEngine
import org.apache.commons.lang.ClassUtils
import org.codehaus.groovy.grails.commons.GrailsDomainClassProperty
import org.hibernate.criterion.CriteriaSpecification

class QuickSearchService {

   static transactional = false

   def grailsApplication

   private static final ALIAS_JOIN_TYPE = CriteriaSpecification.LEFT_JOIN

   /**
    * Executes the search based on given query and properties which should be searched.
    *
    * @param settings Map of settings attributes for the search:
    *    <ul>
    *       <li>
    *          <b>domainClass</b> [Class] (required) - Domain class which should be used for search.
    *       </li>
    *       <li>
    *          <b>searchParams</b> [Map] (optional) - Map of params for sort, order, max, offset, etc. Sort attribute
    *          supports nested sorting, i.e. 'home.person.name'. The map can contain also distinct key with value
    *          of property which should be distinct. This is applicable for searching in nested queries during
    *          auto-complete where the user is interested in one distinct value, [distinct: 'home.person.name']. Beware
    *          that using the distinct returns list of distinct values not domain objects.
    *       </li>
    *       <li>
    *          <b>query</b> [String] (required) - The actual query which should be searched.
    *       </li>
    *       <li>
    *          <b>searchProperties</b> [Map] (optional) - Persistent properties which should be used for the actual
    *          query. Key of the map is any unique key and value is a string representation of the property, nesting
    *          is also supported, i.e. [personName: 'home.person.name']. If searchProperties are not defined, all
    *          String and number persistent properties of the domain class is taken into account.
    *       </li>
    *       <li>
    *          <b>customCriteria</b> [Closure] (optional) - Create criteria closure which is injected into the search
    *          query. This closure is put into the and{...} statement. List of all created aliases (build during
    *          the nesting) is traversed back to the closure and therefore could be used.
    *       </li>
    *       <li>
    *          <b>tokens</b> [List] (optional) - List of tokens which should be used for tokenizing given query string.
    *          If not set, the default config value is used.
    *       </li>
    *       <li>
    *          <b>tokenizeNumbers</b> [Boolean] (optional) - Specifies if the query should be split for numbers and
    *          other characters. If not set, the default config value is used.
    *       </li>
    *       <li>
    *          <b>tokenWrapper</b> [Character] (optional) - Specifies the character which is used to determine no wrapping,
    *          i.e. quotation mark "My Name" will search without tokenizing.
    *       </li>
    *    </ul>
    * @return PagedResulList with searched items.
    */
   def search(Map settings) {
      def domainClass = settings.domainClass
      def searchParams = settings.searchParams
      def customClosure = settings.customClosure
      def queries = QuickSearchUtil.splitQuery(grailsApplication, settings.query, settings.tokens,
         settings.tokenizeNumbers, settings.tokenWrapper)
      def aliasesList = [] // list of created aliases
      def searchProperties = settings.searchProperties ? settings.searchProperties.values().toList() : QuickSearchUtil.getDomainClassProperties(grailsApplication, domainClass)

      // execute criteria query
      def result = domainClass.createCriteria().list(searchParams?.findAll { it.key == "offset" || it.key == "max" }) {
         aliasBuilder.delegate = delegate
         propertyQueryBuilder.delegate = delegate

         // sorting
         if (searchParams?.sort && searchParams?.order) {
            def sortAlias = aliasBuilder(domainClass, searchParams.sort, aliasesList)
            order(sortAlias, searchParams.order)
         }

         // distinct
         projections {
            if (searchParams?.distinct) {
               def distinctAlias = aliasBuilder(domainClass, searchParams.distinct, aliasesList)
               distinct(distinctAlias)
            } else {
               distinct('id')
            }
         }

         and {
            // actual search query
            if (queries?.size() > 0) {
               or {
                  searchProperties.each { property ->
                     // build aliases for property if needed
                     def propertyAlias = aliasBuilder(domainClass, property, aliasesList)
                     queries.each { query ->
                        propertyQueryBuilder(domainClass, property, propertyAlias, query)
                     }
                  }
               }
            }

            // user additional query
            if (customClosure) {
               customClosure.delegate = delegate
               customClosure(aliasesList)
            }
         }
      }

      // build a fake PagedResultList if distinct was not set
      if (!searchParams?.distinct) {
         // reconstruct the list with given order
         if (result.size() > 0) {
            def pagedResult = domainClass.createCriteria().list([:]) {
               aliasBuilder.delegate = delegate
               // sorting
               if (searchParams?.sort && searchParams?.order) {
                  def sortAlias = aliasBuilder(domainClass, searchParams.sort, [])
                  order(sortAlias, searchParams.order)
               }
               // get by ids
               'in'("id", result)
            }
            pagedResult.totalCount = result.totalCount // fake total count
            return pagedResult
         }
      }
      return result
   }

   /**
    * Executes the search based on given query and properties which should be searched. In addition it transforms
    * the result into a list of strings based on given template string.
    *
    * @param settings Map of settings attributes for the search (the same attributes as for search() function).
    * In addition you can specify following attributes:
    *    <ul>
    *       <li>
    *          <b>autocompleteTemplate</b> [String] (optional) - GString representation of final item. The dollar values
    *          should use the keys specified in searchProperties if given or domain class property names. If this
    *          attribute is not specified, it uses toString() method of the domain class. If the searchParams attribute
    *          sets a distinct property the autocompleteTemplate is neglected because the distinct properties are
    *          returned instead.
    *       </li>
    *    </ul>
    * @return List of Maps with given result. The map consists of id and label, i.e. [[id: 10, label: "Graeme Rocher"], [id: 1, label: "Burt Beckwith"]]
    */
   def searchAutoComplete(Map settings) {
      def searchProperties = settings.searchProperties ?: QuickSearchUtil.getDomainClassProperties(grailsApplication, settings.domainClass).collectEntries { [(it): it] }

      // execute the search and transform the results
      return search(settings).collect { entry ->
         def label = ""
         if (settings.autocompleteTemplate && !settings.searchParams?.distinct) {
            def templateProp = searchProperties.collectEntries { k, v -> [(k): QuickSearchUtil.getPropertyByDotName(entry, v)] }
            // add additional properties
            def queries = QuickSearchUtil.splitQuery(grailsApplication, settings.query, settings.tokens,
               settings.tokenizeNumbers, settings.tokenWrapper)
            templateProp.matchResults = QuickSearchUtil.findMatchResults(entry, searchProperties, queries)
            // compose the template
            def engine = new SimpleTemplateEngine()
            label = engine.createTemplate(settings.autocompleteTemplate).make(templateProp).toString()
         } else {
            label = entry.toString()
         }
         return [id: entry.hasProperty("id") ? entry.id : "", label: label]
      }
   }

   /**
    * Alias builder which is responsible for criteria aliases
    */
   def aliasBuilder = { def domainClass, def associationString, def aliasesCreated ->
      def associations = associationString.split("\\.")
      def previousAlias
      def finalAlias = null
      def previousDomainClass = grailsApplication.getDomainClass(domainClass.name)

      for (def i = 0; i < associations.size(); i++) {
         def actualAlias = associations[i]
         def property = previousDomainClass?.getPropertyByName(actualAlias)
         // last is the final property
         if (i + 1 == associations.size()) {
            if (previousAlias)
               finalAlias = "$previousAlias.$actualAlias"
            else
               finalAlias = actualAlias
         } else {
            def aliasLabel
            def alias
            // if not embedded association
            if (!property.isEmbedded()) {
               // compute alias
               if (previousAlias) {
                  aliasLabel = "${previousAlias}_$actualAlias"
                  alias = "$previousAlias.$actualAlias"
               } else {
                  aliasLabel = "_$actualAlias"
                  alias = actualAlias
               }
               // if alias was not created before
               if (!aliasesCreated.contains(aliasLabel)) {
                  createAlias(alias, aliasLabel, ALIAS_JOIN_TYPE)
                  aliasesCreated.add(aliasLabel)
               }
            } else {
               if (previousAlias) {
                  aliasLabel = "$previousAlias.$actualAlias"
               } else {
                  aliasLabel = actualAlias
               }

            }
            // set previous alias
            previousAlias = aliasLabel
            // get the domain class if it is a reference
            previousDomainClass = property.getReferencedDomainClass()
         }
      }

      return finalAlias
   }

   /**
    * The actual query builder
    */
   def propertyQueryBuilder = { def domainClass, def property, def propertyAlias, def queryString ->
      def propertyType = (property instanceof GrailsDomainClassProperty) ? property.getType() : QuickSearchUtil.getPropertyType(grailsApplication, domainClass, property)

      // set search
      if (propertyType == String) {
         ilike(propertyAlias, "%${queryString}%")
      } else if (ClassUtils.isAssignable(propertyType, Number.class, true)) {
         if (queryString.isNumber())
            try {
               def number = queryString.asType(propertyType)
               eq(propertyAlias, number)
            }
            catch (NumberFormatException e) {
               log.warn "Queried string [$queryString] could not be translated to number."
            }
      } else {
         log.error "Unsupported class type [$propertyType] for quick search, omitting."
      }
   }
}
