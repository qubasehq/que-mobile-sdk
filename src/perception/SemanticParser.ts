/**
 * SemanticParser - Parses Android UI hierarchy XML into simplified element list
 * 
 * Responsibilities:
 * - Parse XML using fast-xml-parser
 * - Extract element attributes (bounds, text, resourceId, className)
 * - Deduplicate identical elements
 * - Generate unique numeric IDs
 * - Format hierarchical UI representation string
 */

import { XMLParser } from 'fast-xml-parser'
import { Element } from '../types'

interface ParsedNode {
  '@_class'?: string
  '@_resource-id'?: string
  '@_text'?: string
  '@_content-desc'?: string
  '@_clickable'?: string
  '@_bounds'?: string
  '@_enabled'?: string
  '@_focusable'?: string
  '@_scrollable'?: string
  node?: ParsedNode | ParsedNode[]
}

interface ParsedElement {
  className: string
  resourceId: string
  text: string
  contentDesc: string
  isClickable: boolean
  bounds: string
  center: { x: number; y: number }
  isEnabled: boolean
  isFocusable: boolean
  isScrollable: boolean
}

export class SemanticParser {
  private parser: XMLParser
  private elementIdCounter: number = 0
  private seenElements: Set<string> = new Set()

  constructor() {
    // Configure fast-xml-parser
    this.parser = new XMLParser({
      ignoreAttributes: false,
      attributeNamePrefix: '@_',
      parseAttributeValue: false,
      trimValues: true,
    })
  }

  /**
   * Parse XML hierarchy and return elements with UI representation
   */
  public parse(xml: string): {
    elements: Element[]
    uiRepresentation: string
    elementMap: Map<number, Element>
  } {
    // Reset state for new parse
    this.elementIdCounter = 0
    this.seenElements.clear()

    try {
      const parsed = this.parser.parse(xml)
      const rootNode = parsed?.hierarchy?.node

      if (!rootNode) {
        return {
          elements: [],
          uiRepresentation: 'Empty screen - no elements found',
          elementMap: new Map(),
        }
      }

      // Extract all elements from hierarchy
      const elements: Element[] = []
      const uiLines: string[] = []
      
      this.traverseNode(rootNode, 0, elements, uiLines)

      // Build element map
      const elementMap = new Map<number, Element>()
      elements.forEach((element) => {
        elementMap.set(element.id, element)
      })

      // Build UI representation
      const uiRepresentation = uiLines.length > 0 
        ? uiLines.join('\n')
        : 'Empty screen - no interactive elements found'

      return {
        elements,
        uiRepresentation,
        elementMap,
      }
    } catch (error) {
      console.error('Failed to parse XML:', error)
      return {
        elements: [],
        uiRepresentation: 'Error parsing screen hierarchy',
        elementMap: new Map(),
      }
    }
  }

  /**
   * Traverse XML node tree recursively
   */
  private traverseNode(
    node: ParsedNode | ParsedNode[],
    depth: number,
    elements: Element[],
    uiLines: string[]
  ): void {
    const nodes = Array.isArray(node) ? node : [node]

    for (const n of nodes) {
      const element = this.extractElement(n)
      
      if (element) {
        // Check if this element should be included
        if (this.shouldIncludeElement(element)) {
          const elementKey = this.getElementKey(element)
          
          // Deduplicate: only add if not seen before
          if (!this.seenElements.has(elementKey)) {
            this.seenElements.add(elementKey)
            
            const id = this.elementIdCounter++
            const elementObj: Element = {
              id,
              description: this.buildDescription(element),
              bounds: element.bounds,
              center: element.center,
              isClickable: element.isClickable,
              resourceId: element.resourceId || undefined,
              className: element.className || undefined,
              text: element.text || undefined,
            }
            
            elements.push(elementObj)
            
            // Add to UI representation with indentation
            const indent = '  '.repeat(depth)
            const desc = this.buildDescription(element)
            uiLines.push(`${indent}[${id}] ${desc}`)
          }
        }
      }

      // Recursively process child nodes
      if (n.node) {
        this.traverseNode(n.node, depth + 1, elements, uiLines)
      }
    }
  }

  /**
   * Extract element data from parsed node
   */
  private extractElement(node: ParsedNode): ParsedElement | null {
    const bounds = node['@_bounds']
    if (!bounds) {
      return null
    }

    const center = this.parseBoundsToCenter(bounds)
    if (!center) {
      return null
    }

    return {
      className: node['@_class'] || '',
      resourceId: node['@_resource-id'] || '',
      text: node['@_text'] || '',
      contentDesc: node['@_content-desc'] || '',
      isClickable: node['@_clickable'] === 'true',
      bounds,
      center,
      isEnabled: node['@_enabled'] === 'true',
      isFocusable: node['@_focusable'] === 'true',
      isScrollable: node['@_scrollable'] === 'true',
    }
  }

  /**
   * Parse bounds string "[left,top][right,bottom]" to center coordinates
   */
  private parseBoundsToCenter(bounds: string): { x: number; y: number } | null {
    try {
      // Format: "[left,top][right,bottom]"
      const match = bounds.match(/\[(\d+),(\d+)\]\[(\d+),(\d+)\]/)
      if (!match) {
        return null
      }

      const left = parseInt(match[1], 10)
      const top = parseInt(match[2], 10)
      const right = parseInt(match[3], 10)
      const bottom = parseInt(match[4], 10)

      return {
        x: Math.floor((left + right) / 2),
        y: Math.floor((top + bottom) / 2),
      }
    } catch {
      return null
    }
  }

  /**
   * Determine if element should be included in output
   */
  private shouldIncludeElement(element: ParsedElement): boolean {
    // Include if clickable
    if (element.isClickable) {
      return true
    }

    // Include if has meaningful text
    if (element.text && element.text.trim().length > 0) {
      return true
    }

    // Include if has content description
    if (element.contentDesc && element.contentDesc.trim().length > 0) {
      return true
    }

    // Include if scrollable
    if (element.isScrollable) {
      return true
    }

    return false
  }

  /**
   * Build human-readable description for element
   */
  private buildDescription(element: ParsedElement): string {
    const parts: string[] = []

    // Add text if available
    if (element.text && element.text.trim()) {
      parts.push(`"${element.text.trim()}"`)
    }

    // Add content description if available and different from text
    if (element.contentDesc && element.contentDesc.trim() && element.contentDesc !== element.text) {
      parts.push(`(${element.contentDesc.trim()})`)
    }

    // Add resource ID if available
    if (element.resourceId) {
      const shortId = element.resourceId.split('/').pop() || element.resourceId
      parts.push(`#${shortId}`)
    }

    // Add class name (simplified)
    if (element.className) {
      const shortClass = element.className.split('.').pop() || element.className
      parts.push(`<${shortClass}>`)
    }

    // Add interaction hints
    const hints: string[] = []
    if (element.isClickable) hints.push('clickable')
    if (element.isScrollable) hints.push('scrollable')
    if (hints.length > 0) {
      parts.push(`[${hints.join(', ')}]`)
    }

    return parts.length > 0 ? parts.join(' ') : 'Unknown element'
  }

  /**
   * Generate unique key for element deduplication
   */
  private getElementKey(element: ParsedElement): string {
    // Use combination of properties to identify duplicates
    return [
      element.className,
      element.resourceId,
      element.text,
      element.contentDesc,
      element.bounds,
      element.isClickable.toString(),
    ].join('|')
  }
}
