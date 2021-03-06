Feature: Test search with facets on Alien

  Background:
    Given I am authenticated with "COMPONENTS_MANAGER" role
    And I upload the archive "tosca-normative-types-1.0.0-SNAPSHOT"

  @reset
  Scenario: Facet search should returns result with proper facets
    When I search for "node types" from 0 with result size of 1000
    Then The search result should contain 12 data with 5 facets and some of them are:
      | abstract            | T                            | 4  |
      | capabilities.type   | tosca.capabilities.node      | 12 |
      | capabilities.type   | tosca.capabilities.container | 4  |
      | derivedFrom         |                              | 1  |
      | derivedFrom         | tosca.nodes.root             | 11 |
      | defaultCapabilities |                              | 12 |